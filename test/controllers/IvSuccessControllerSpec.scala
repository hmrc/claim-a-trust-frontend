/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import base.SpecBase
import cats.data.EitherT
import ch.qos.logback.classic.Level
import connectors.TaxEnrolmentsConnector
import errors.{ServerError, TrustErrors, UpstreamTaxEnrolmentsError}
import models.auditing.Events.{CLAIM_A_TRUST_ERROR, CLAIM_A_TRUST_SUCCESS}
import models.{EnrolmentCreated, EnrolmentResponse, NormalMode, TaxEnrolmentsRequest, UserAnswers}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.{Application, Logger}
import repositories.SessionRepository
import services.*
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import views.html.IvSuccessView

import scala.concurrent.Future

class IvSuccessControllerSpec extends SpecBase with BeforeAndAfterEach with EitherValues with LogCapturing {

  private val utr = "0987654321"
  private val urn = "ABTRUST12345678"

  private val connector                      = mock[TaxEnrolmentsConnector]
  private val mockRelationshipEstablishment  = mock[RelationshipEstablishment]
  private val mockAuditService: AuditService = mock[AuditService]

  // Mock mongo repository
  private val mockRepository = mock[SessionRepository]

  override def beforeEach(): Unit = {
    reset(connector)
    reset(mockRelationshipEstablishment)
    reset(mockRepository)
    reset(mockAuditService)
    super.beforeEach()
  }

  private val controllerLogger: Logger = Logger(classOf[IvSuccessController])

  private val sessionId = "session-12345"

  private def logPrefix(functionName: String): String =
    s"[IvSuccessController][$functionName][Session ID: $sessionId]"

  private def relationshipEstablishedLog(identifier: String): (Level, String) =
    Level.INFO -> (s"${logPrefix("onPageLoad")}" +
      s" relationship is already established in IV for $identifier, sending user to successfully claimed")

  private def enrolledLog(identifier: String): (Level, String) =
    Level.INFO -> (s"${logPrefix("onRelationshipFound")} successfully enrolled $identifier to users" +
      " credential after passing Trust IV, user can now maintain the trust")

  private def enrolmentFailedLog(identifier: String): (Level, String) =
    Level.ERROR -> (s"${logPrefix("onRelationshipFound")} failed to create enrolment for $identifier" +
      " with tax-enrolments, users credential has not been updated, user needs to claim again")

  private def onPageLoadRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.IvSuccessController.onPageLoad.url).withSession(SessionKeys.sessionId -> sessionId)

  private def buildApplication(userAnswers: UserAnswers): Application =
    applicationBuilder(
      userAnswers = Some(userAnswers),
      relationshipEstablishment = mockRelationshipEstablishment
    ).overrides(
      bind[TaxEnrolmentsConnector].toInstance(connector),
      bind[SessionRepository].toInstance(mockRepository),
      bind[AuditService].toInstance(mockAuditService)
    ).build()

  private def stubRelationship(identifier: String, response: Either[TrustErrors, RelationEstablishmentStatus]): Unit =
    when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(identifier))(using any()))
      .thenReturn(EitherT[Future, TrustErrors, RelationEstablishmentStatus](Future.successful(response)))

  private def stubEnrol(identifier: String, response: Either[TrustErrors, EnrolmentResponse]): Unit =
    when(connector.enrol(eqTo(TaxEnrolmentsRequest(identifier)))(using any(), any(), any()))
      .thenReturn(EitherT[Future, TrustErrors, EnrolmentResponse](Future.successful(response)))

  private def stubRepositorySet(response: Either[TrustErrors, Boolean]): Unit =
    when(mockRepository.set(any()))
      .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(response)))

  "IvSuccess Controller" when {

    "claiming a trust" must {

      "return OK with the correct view for a GET with no Agent and set hasEnrolled true" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false)
          .value
          .set(IdentifierPage, utr)
          .value

        val application = buildApplication(userAnswers)

        stubEnrol(utr, Right(EnrolmentCreated))
        stubRepositorySet(Right(true))
        stubRelationship(utr, Right(RelationshipFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = false, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          // Verify if the HasEnrolled value is being set in mongo
          val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).value
          verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))
          verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(false))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolledLog(utr))
        }

        application.stop()
      }

      "when error exception message is nonEmpty" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, utr)
          .value

        val application = buildApplication(userAnswers)

        stubEnrol(utr, Left(ServerError("an exception was returned")))
        stubRepositorySet(Left(ServerError("an exception was returned")))
        stubRelationship(utr, Right(RelationshipFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, onPageLoadRequest).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_ERROR),
            eqTo(utr),
            eqTo("an exception was returned")
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolmentFailedLog(utr))
        }

        application.stop()
      }

      "when error exception message is empty" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, utr)
          .value

        val application = buildApplication(userAnswers)

        stubEnrol(utr, Left(ServerError("")))
        stubRepositorySet(Left(ServerError("")))
        stubRelationship(utr, Right(RelationshipFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, onPageLoadRequest).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_ERROR),
            eqTo(utr),
            eqTo("Encountered an unexpected issue claiming a trust")
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolmentFailedLog(utr))
        }

        application.stop()
      }

      "no relationship found in Trust IV" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false)
          .value
          .set(IdentifierPage, utr)
          .value

        val application = buildApplication(userAnswers)

        stubRelationship(utr, Right(RelationshipNotFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, onPageLoadRequest).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

          verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(using any(), any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> (s"${logPrefix("onPageLoad")} no relationship found in Trust IV," +
              " cannot continue with enrolling the credential, sending the user back to the start of Trust IV")
          )
        }

        application.stop()
      }

      "return OK with the correct view for a GET with Agent and set hasEnrolled true" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, utr)
          .value

        val application = buildApplication(userAnswers)

        stubRepositorySet(Right(true))
        stubRelationship(utr, Right(RelationshipFound))
        stubEnrol(utr, Right(EnrolmentCreated))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = true, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).value
          verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))
          verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(true))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolledLog(utr))
        }

        application.stop()
      }
    }

    "claiming a trust again after a failure" must {

      "return OK with the correct view for a GET with no Agent and set hasEnrolled true" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false)
          .value
          .set(IdentifierPage, utr)
          .value
          .set(HasEnrolled, false)
          .value

        val application = buildApplication(userAnswers)

        stubRepositorySet(Right(true))
        stubRelationship(utr, Right(RelationshipFound))
        stubEnrol(utr, Right(EnrolmentCreated))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = false, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          // Verify if the HasEnrolled value is being set in mongo
          val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).value
          verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))
          verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(false))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolledLog(utr))
        }

        application.stop()
      }

      "return OK with the correct view for a GET with Agent and set hasEnrolled true" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, utr)
          .value
          .set(HasEnrolled, false)
          .value

        val application = buildApplication(userAnswers)

        stubRepositorySet(Right(true))
        stubRelationship(utr, Right(RelationshipFound))
        stubEnrol(utr, Right(EnrolmentCreated))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = true, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).value
          verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))
          verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(true))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolledLog(utr))
        }

        application.stop()
      }
    }

    "rendering page after having claimed" must {

      "return OK and the correct view for a GET with no Agent and has enrolled" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false)
          .value
          .set(IdentifierPage, utr)
          .value
          .set(HasEnrolled, true)
          .value

        val application = buildApplication(userAnswers)

        stubRelationship(utr, Right(RelationshipFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = false, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          verify(mockRepository, never()).set(any())
          verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService, never()).audit(any(), any(), any())(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr))
        }

        application.stop()
      }

      "return OK and the correct view for a GET with Agent and has enrolled" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, utr)
          .value
          .set(HasEnrolled, true)
          .value

        val application = buildApplication(userAnswers)

        stubRelationship(utr, Right(RelationshipFound))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = true, utr)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          verify(mockRepository, never()).set(any())
          verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
          verify(mockAuditService, never()).audit(any(), any(), any())(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr))
        }

        application.stop()
      }
    }

    "claiming a URN" must {

      "return OK and the correct view for a GET with no Agent" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false)
          .value
          .set(IdentifierPage, urn)
          .value

        val application = buildApplication(userAnswers)

        stubRepositorySet(Right(true))
        stubRelationship(urn, Right(RelationshipFound))
        stubEnrol(urn, Right(EnrolmentCreated))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = false, urn)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          verify(connector).enrol(eqTo(TaxEnrolmentsRequest(urn)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(urn))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(urn), eqTo(false))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(urn), enrolledLog(urn))
        }

        application.stop()
      }

      "return OK and the correct view for a GET with Agent" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true)
          .value
          .set(IdentifierPage, urn)
          .value

        val application = buildApplication(userAnswers)

        stubRepositorySet(Right(true))
        stubRelationship(urn, Right(RelationshipFound))
        stubEnrol(urn, Right(EnrolmentCreated))

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val request = onPageLoadRequest

          val view = application.injector.instanceOf[IvSuccessView]

          val viewAsString = view(isAgent = true, urn)(using request, messages).toString

          val result = route(application, request).value

          status(result) mustEqual OK

          contentAsString(result) mustEqual viewAsString

          verify(connector).enrol(eqTo(TaxEnrolmentsRequest(urn)))(using any(), any(), any())
          verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(urn))(using any())
          verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(urn), eqTo(true))(using any(), any())

          logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(urn), enrolledLog(urn))
        }

        application.stop()
      }
    }

    "redirect to maintain" when {

      "user continues and checks status of the trust" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        val request = FakeRequest(POST, routes.IvSuccessController.onSubmit.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual "http://localhost:9788/maintain-a-trust/status"

        application.stop()
      }
    }

    "redirect to Session Expired" when {

      "no existing data is found" in {
        val application = applicationBuilder(userAnswers = None).build()

        val result = route(application, onPageLoadRequest).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

      "no identifier is found" in {
        val application = applicationBuilder(userAnswers = Some(UserAnswers(userAnswersId))).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, onPageLoadRequest).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> (s"${logPrefix("onPageLoad")} no identifier found in user answers," +
              " unable to continue with enrolling credential and claiming the trust on behalf of the user")
          )
        }

        application.stop()
      }

      "redirect to Internal Server Error" when {

        "tax enrolments fails" when {

          "401 UNAUTHORIZED" in {
            val utr = "1234567890"

            val userAnswers = UserAnswers(userAnswersId)
              .set(IsAgentManagingTrustPage, true)
              .value
              .set(IdentifierPage, utr)
              .value

            val application = buildApplication(userAnswers)

            stubRepositorySet(Right(true))
            stubRelationship(utr, Right(RelationshipFound))
            stubEnrol(utr, Left(UpstreamTaxEnrolmentsError("Unauthorized")))

            withCaptureOfLoggingFrom(controllerLogger) { logs =>
              val result = route(application, onPageLoadRequest).value

              status(result) mustEqual INTERNAL_SERVER_ERROR

              // Verify if the HasEnrolled value is being unset in mongo in case of errors
              val userAnswersWithHasEnrolledUnset = userAnswers.set(HasEnrolled, false).value
              verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolledUnset))
              verify(connector).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
              verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
              verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_ERROR), eqTo(utr), eqTo("Unauthorized"))(using
                any(),
                any()
              )

              logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolmentFailedLog(utr))
            }

            application.stop()
          }

          "400 BAD_REQUEST" in {
            val userAnswers = UserAnswers(userAnswersId)
              .set(IsAgentManagingTrustPage, true)
              .value
              .set(IdentifierPage, utr)
              .value

            val application = buildApplication(userAnswers)

            stubRepositorySet(Right(true))
            stubRelationship(utr, Right(RelationshipFound))
            stubEnrol(utr, Left(UpstreamTaxEnrolmentsError("BadRequest")))

            withCaptureOfLoggingFrom(controllerLogger) { logs =>
              val result = route(application, onPageLoadRequest).value

              status(result) mustEqual INTERNAL_SERVER_ERROR

              // Verify if the HasEnrolled value is being unset in mongo in case of errors
              val userAnswersWithHasEnrolledUnset = userAnswers.set(HasEnrolled, false).value
              verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolledUnset))
              verify(connector).enrol(eqTo(TaxEnrolmentsRequest(utr)))(using any(), any(), any())
              verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(using any())
              verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_ERROR), eqTo(utr), eqTo("BadRequest"))(using
                any(),
                any()
              )

              logMessagesWithLevel(logs) mustBe List(relationshipEstablishedLog(utr), enrolmentFailedLog(utr))
            }

            application.stop()
          }

          "onPageLoad fails" in {
            val userAnswers = UserAnswers(userAnswersId)
              .set(IsAgentManagingTrustPage, true)
              .value
              .set(IdentifierPage, utr)
              .value

            stubRelationship(utr, Left(ServerError()))

            val application = buildApplication(userAnswers)

            withCaptureOfLoggingFrom(controllerLogger) { logs =>
              val result = route(application, onPageLoadRequest).value

              status(result) mustEqual INTERNAL_SERVER_ERROR
              contentType(result) mustBe Some("text/html")

              verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(using any(), any(), any())

              logMessagesWithLevel(logs) mustBe List(
                Level.WARN -> s"${logPrefix("onPageLoad")} Error while loading page"
              )
            }

            application.stop()
          }
        }
      }
    }

    "redirect to trusts registration page" when {

      "user has followed a bookmark or manipulated the URL" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        val request = FakeRequest(GET, controllers.routes.IvSuccessController.questionTamper.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual frontendAppConfig.trustsRegistration

        application.stop()
      }
    }
  }

}
