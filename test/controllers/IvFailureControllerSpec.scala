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
import connectors.{RelationshipEstablishmentConnector, TrustsStoreConnector}
import errors.{ServerError, TrustErrors, UpstreamRelationshipError}
import models.RelationshipEstablishmentStatus.RelationshipEstablishmentStatus
import models.auditing.Events.CLAIM_A_TRUST_FAILURE
import models.auditing.FailureReasons
import models.{RelationshipEstablishmentStatus, TrustsStoreRequest, UserAnswers}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.{Application, Logger}
import services.AuditService
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.Future

class IvFailureControllerSpec extends SpecBase with EitherValues with LogCapturing {

  lazy val connector: RelationshipEstablishmentConnector = mock[RelationshipEstablishmentConnector]

  private val mockAuditService: AuditService = mock[AuditService]

  val answers: UserAnswers = emptyUserAnswers
    .set(IdentifierPage, "1234567890")
    .value
    .set(IsAgentManagingTrustPage, true)
    .value

  private def buildApplication(): Application =
    applicationBuilder(userAnswers = Some(answers))
      .overrides(
        bind[RelationshipEstablishmentConnector].toInstance(connector),
        bind[AuditService].toInstance(mockAuditService)
      )
      .build()

  val onIvFailureRoute: String = routes.IvFailureController.onTrustIvFailure.url
  val journeyId                = "47a8a543-6961-4221-86e8-d22e2c3c91de"

  private val controllerLogger: Logger = Logger(classOf[IvFailureController])

  private val sessionId = "session-12345"

  private def logPrefix(functionName: String): String =
    s"[IvFailureController][$functionName][Session ID: $sessionId]"

  private def getRequest(url: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> sessionId)

  private def stubJourneyId(response: Either[TrustErrors, RelationshipEstablishmentStatus]): Unit =
    when(connector.journeyId(any[String])(using any(), any()))
      .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(response)))

  "IvFailure Controller" must {

    "callback-failure route" when {

      "redirect to IV FallbackFailure when no journeyId is provided" in {
        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(onIvFailureRoute)).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.IV_TECHNICAL_PROBLEM_NO_JOURNEY_ID)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> s"${logPrefix("onTrustIvFailure")} unable to retrieve a journeyId to determine the reason"
          )
        }

        application.stop()
      }

      "redirect to trust locked page when user fails Trusts IV after multiple attempts" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.Locked))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvFailureController.trustLocked.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.LOCKED)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> s"${logPrefix("renderFailureReason")} 1234567890 is locked"
          )
        }

        application.stop()
      }

      "redirect to trust utr not found page when the utr isn't found" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.NotFound))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvFailureController.trustNotFound.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.IDENTIFIER_NOT_FOUND)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> s"${logPrefix("renderFailureReason")} 1234567890 was not found"
          )
        }

        application.stop()
      }

      "redirect to trust utr in processing page when the utr is processing" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.InProcessing))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvFailureController.trustStillProcessing.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.TRUST_STILL_PROCESSING)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> s"${logPrefix("renderFailureReason")} 1234567890 is processing"
          )
        }

        application.stop()
      }

      "redirect to trust registration page for Question Tamper flow" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.QuestionTamper))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvSuccessController.questionTamper.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.QUESTION_TAMPER)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> (s"${logPrefix("renderFailureReason")}" +
              " User has followed a bookmark or otherwise manipulated the url for 1234567890")
          )
        }

        application.stop()
      }

      "redirect to could not confirm identity page for an unsupported relationship status" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.UnsupportedRelationshipStatus("SOMETHING_NEW")))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.CouldNotConfirmIdentityController.onPageLoad.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.UNSUPPORTED_RELATIONSHIP_STATUS)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> s"${logPrefix("renderFailureReason")} Unsupported IV failure reason: SOMETHING_NEW"
          )
        }

        application.stop()
      }

      "redirect to IV FallbackFailure for an upstream relationship error" in {
        stubJourneyId(Left(UpstreamRelationshipError("Unexpected HTTP response code 500")))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.UPSTREAM_RELATIONSHIP_ERROR)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> s"${logPrefix("renderFailureReason")} HTTP response: Unexpected HTTP response code 500"
          )
        }

        application.stop()
      }

      "redirect to IV FallbackFailure when no error key found in response" in {
        stubJourneyId(Right(RelationshipEstablishmentStatus.NoRelationshipStatus))

        val application = buildApplication()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url

          verify(mockAuditService).auditFailure(
            eqTo(CLAIM_A_TRUST_FAILURE),
            eqTo("1234567890"),
            eqTo(FailureReasons.IV_TECHNICAL_PROBLEM_NO_ERROR_KEY)
          )(using any(), any())

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> s"${logPrefix("renderFailureReason")} No errorKey in HTTP response"
          )
        }

        application.stop()
      }
    }

    "redirect to FallbackFailureController when unable to retrieve identifier" in {
      val answers = emptyUserAnswers
        .set(IsAgentManagingTrustPage, true)
        .value

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(
          bind[RelationshipEstablishmentConnector].toInstance(connector),
          bind[AuditService].toInstance(mockAuditService)
        )
        .build()

      stubJourneyId(Right(RelationshipEstablishmentStatus.NoRelationshipStatus))

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(s"$onIvFailureRoute?journeyId=$journeyId")).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.ERROR -> s"${logPrefix("onTrustIvFailure")} unable to retrieve an identifier from mongo"
        )
      }

      application.stop()
    }
  }

  "locked route" when {

    val onLockedRoute  = routes.IvFailureController.trustLocked.url
    val utr            = "3000000001"
    val managedByAgent = true
    val trustLocked    = true

    val lockedAnswers = emptyUserAnswers
      .set(IdentifierPage, utr)
      .value
      .set(IsAgentManagingTrustPage, true)
      .value

    "return OK and the correct view for a GET for locked route" in {
      val connector = mock[TrustsStoreConnector]

      when(
        connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using
          any(),
          any(),
          any()
        )
      )
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

      val application = applicationBuilder(userAnswers = Some(lockedAnswers))
        .overrides(bind[TrustsStoreConnector].toInstance(connector), bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(onLockedRoute)).value

        status(result) mustEqual OK

        contentAsString(result) must include(
          "As you have had 3 unsuccessful tries at accessing this trust you will need to try again in 30 minutes."
        )

        verify(connector)
          .claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using any(), any(), any())

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> s"${logPrefix("trustLocked")} failed IV 3 times, $utr trust is locked out from IV"
        )
      }

      application.stop()
    }

    "return Internal Server when trustLocked fails" in {
      val connector = mock[TrustsStoreConnector]

      when(
        connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using
          any(),
          any(),
          any()
        )
      )
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

      // Previously didn't bind the mock, so this relied on the real connector failing to reach localhost:9783
      val application = applicationBuilder(userAnswers = Some(lockedAnswers))
        .overrides(bind[TrustsStoreConnector].toInstance(connector), bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(onLockedRoute)).value

        status(result) mustEqual INTERNAL_SERVER_ERROR
        contentType(result) mustBe Some("text/html")

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("trustLocked")} Error while storing user answers"
        )
      }

      application.stop()
    }

    "return session expired when GET for locked route" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(onLockedRoute)).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("trustLocked")} unable to determine if trust was locked out from IV"
        )
      }

      application.stop()
    }

    "return OK and the correct view for a GET for not found route" in {
      val answers = emptyUserAnswers
        .set(IdentifierPage, "1234567890")
        .value

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(routes.IvFailureController.trustNotFound.url)).value

        status(result) mustEqual OK

        contentAsString(result) must include("The unique identifier you gave for the trust does not match our records")

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> s"${logPrefix("trustNotFound")} IV was unable to find the trust for 1234567890"
        )
      }

      application.stop()
    }

    "return session expired when GET for not found route" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(routes.IvFailureController.trustNotFound.url)).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> (s"${logPrefix("trustNotFound")}" +
            " no identifier stored in user answers when informing user the trust was not found")
        )
      }

      application.stop()
    }

    "return OK and the correct view for a GET for still processing route" in {
      val answers = emptyUserAnswers
        .set(IdentifierPage, "1234567891")
        .value

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(routes.IvFailureController.trustStillProcessing.url)).value

        status(result) mustEqual OK

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> s"${logPrefix("trustStillProcessing")} IV determined the trust 1234567891 was still processing"
        )
      }

      application.stop()
    }

    "return session expired when GET for still processing route" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest(routes.IvFailureController.trustStillProcessing.url)).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> (s"${logPrefix("trustStillProcessing")}" +
            " no identifier stored in user answers when informing user trust was still processing")
        )
      }

      application.stop()
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None)
        .overrides(bind[AuditService].toInstance(mockAuditService))
        .build()

      val result = route(application, getRequest(onLockedRoute)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

      application.stop()
    }
  }

}
