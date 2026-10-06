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
import errors.{NoData, ServerError, TrustErrors}
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.IdentifierPage
import play.api.Logger
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.{FakeRelationshipEstablishmentService, RelationshipNotFound}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.Future

class SaveIdentifierControllerSpec extends SpecBase with EitherValues with LogCapturing {

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val fakeEstablishmentServiceFailing = new FakeRelationshipEstablishmentService(Right(RelationshipNotFound))
  val fakeEstablishmentServiceError   = new FakeRelationshipEstablishmentService(Left(ServerError()))

  private val controllerLogger: Logger = Logger(classOf[SaveIdentifierController])

  private val sessionId = "session-12345"

  private def logPrefix(functionName: String): String =
    s"[SaveIdentifierController][$functionName][Session ID: $sessionId]"

  private def saveRequest(identifier: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.SaveIdentifierController.save(identifier).url)
      .withSession(SessionKeys.sessionId -> sessionId)

  private def startedJourneyLog(identifier: String): (Level, String) =
    Level.INFO -> s"${logPrefix("saveAndContinue")} user has started the claim a trust journey for $identifier"

  private val storeErrorLogs: List[(Level, String)] = List(
    Level.WARN -> s"${logPrefix("saveAndContinue")} Error while storing user answers",
    Level.WARN -> s"${logPrefix("save")} Could not save identifier"
  )

  private def stubRepositorySet(
    repository: SessionRepository,
    response: Either[TrustErrors, Boolean]
  ): ArgumentCaptor[UserAnswers] = {
    val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
    when(repository.set(captor.capture()))
      .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(response)))
    captor
  }

  "SaveIdentifierController" when {

    "invalid identifier provided" must {

      "render an error page" in {
        val mockSessionRepository = mock[SessionRepository]

        val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, saveRequest("123")).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustBe routes.FallbackFailureController.onPageLoad.url

          verify(mockSessionRepository, never()).set(any())

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> s"${logPrefix("getIdentifier")} Identifier provided is not a valid URN or UTR"
          )
        }

        application.stop()
      }
    }

    "could not save identifier" must {

      "render an error page" in {
        val mockSessionRepository = mock[SessionRepository]

        stubRepositorySet(mockSessionRepository, Left(NoData))

        val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, saveRequest(urn)).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          logMessagesWithLevel(logs) mustBe storeErrorLogs
        }

        application.stop()
      }
    }

    "utr provided" must {

      "save UTR to session repo" when {

        "user answers does not exist" in {
          val mockSessionRepository = mock[SessionRepository]

          val captor = stubRepositorySet(mockSessionRepository, Right(true))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(utr)).value

            status(result) mustEqual SEE_OTHER

            redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

            captor.getValue.get(IdentifierPage).value mustBe utr

            logMessagesWithLevel(logs) mustBe List(startedJourneyLog(utr))
          }

          application.stop()
        }

        "user answers exists" in {
          val mockSessionRepository = mock[SessionRepository]

          val captor = stubRepositorySet(mockSessionRepository, Right(true))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(utr)).value

            status(result) mustEqual SEE_OTHER

            redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

            captor.getValue.get(IdentifierPage).value mustBe utr

            logMessagesWithLevel(logs) mustBe List(startedJourneyLog(utr))
          }

          application.stop()
        }
      }
    }

    "urn provided" must {

      "save URN to session repo" when {

        "user answers does not exist" in {
          val mockSessionRepository = mock[SessionRepository]

          val captor = stubRepositorySet(mockSessionRepository, Right(true))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(urn)).value

            status(result) mustEqual SEE_OTHER

            redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

            captor.getValue.get(IdentifierPage).value mustBe urn

            logMessagesWithLevel(logs) mustBe List(startedJourneyLog(urn))
          }

          application.stop()
        }

        "user answers exists" in {
          val mockSessionRepository = mock[SessionRepository]

          val captor = stubRepositorySet(mockSessionRepository, Right(true))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(urn)).value

            status(result) mustEqual SEE_OTHER

            redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

            captor.getValue.get(IdentifierPage).value mustBe urn

            logMessagesWithLevel(logs) mustBe List(startedJourneyLog(urn))
          }

          application.stop()
        }

        "return an internal server error when user answers do not exist" in {
          val mockSessionRepository = mock[SessionRepository]

          val captor = stubRepositorySet(mockSessionRepository, Left(ServerError()))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(urn)).value

            status(result) mustEqual INTERNAL_SERVER_ERROR
            contentType(result) mustBe Some("text/html")

            captor.getValue.get(IdentifierPage).value mustBe urn

            logMessagesWithLevel(logs) mustBe storeErrorLogs
          }

          application.stop()
        }

        "error while storing user answers" in {
          val answers = emptyUserAnswers
            .set(IdentifierPage, "0987654321")
            .value

          val mockSessionRepository = mock[SessionRepository]

          stubRepositorySet(mockSessionRepository, Left(ServerError()))

          val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(urn)).value

            status(result) mustEqual INTERNAL_SERVER_ERROR
            contentType(result) mustBe Some("text/html")

            logMessagesWithLevel(logs) mustBe storeErrorLogs
          }

          application.stop()
        }

        "user directed to trust claimed" in {
          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(urn)).value

            status(result) mustEqual SEE_OTHER

            redirectLocation(result).value mustEqual routes.IvSuccessController.onPageLoad.url

            logMessagesWithLevel(logs) mustBe List(
              Level.INFO -> (s"${logPrefix("checkIfAlreadyHaveIvRelationship")}" +
                s" relationship is already established in IV for $urn sending user to successfully claimed")
            )
          }

          application.stop()
        }

        "user failed to claim trust" in {
          val mockSessionRepository = mock[SessionRepository]

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceError)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          withCaptureOfLoggingFrom(controllerLogger) { logs =>
            val result = route(application, saveRequest(utr)).value

            status(result) mustEqual INTERNAL_SERVER_ERROR
            contentType(result) mustBe Some("text/html")

            verify(mockSessionRepository, never()).set(any())

            logMessagesWithLevel(logs) mustBe List(
              Level.WARN -> s"${logPrefix("save")} Could not save identifier"
            )
          }

          application.stop()
        }
      }
    }
  }

}
