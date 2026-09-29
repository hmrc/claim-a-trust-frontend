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
import connectors.TrustsStoreConnector
import errors.{ServerError, TrustErrors}
import models.TrustsStoreRequest
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logger
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.{FakeRelationshipEstablishmentService, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import views.html.BeforeYouContinueView

import scala.concurrent.Future

class BeforeYouContinueControllerSpec extends SpecBase with EitherValues with LogCapturing {

  val utr            = "0987654321"
  val managedByAgent = true
  val trustLocked    = false

  val fakeEstablishmentServiceFailing = new FakeRelationshipEstablishmentService(Right(RelationshipNotFound))
  val fakeEstablishmentServiceFound   = new FakeRelationshipEstablishmentService(Right(RelationshipFound))
  val fakeEstablishmentServiceError   = new FakeRelationshipEstablishmentService(Left(ServerError()))

  private val controllerLogger: Logger = Logger(classOf[BeforeYouContinueController])

  private val sessionId = "session-12345"
  private val logPrefix = "[BeforeYouContinueController]"
  private val session   = s"[Session ID: $sessionId]"

  private def getRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.BeforeYouContinueController.onPageLoad.url).withSession(SessionKeys.sessionId -> sessionId)

  private def postRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(POST, routes.BeforeYouContinueController.onSubmit.url).withSession(SessionKeys.sessionId -> sessionId)

  private def noDataError(methodName: String): (Level, String) =
    Level.ERROR -> (s"$logPrefix[$methodName]$session" +
      " no identifier available in user answers, cannot continue with claiming the trust")

  private def storeError(methodName: String): (Level, String) =
    Level.WARN -> s"$logPrefix[$methodName]$session Error while storing user answers"

  "BeforeYouContinue Controller" must {

    "return OK and the correct view for a GET" in {
      val answers = emptyUserAnswers.set(IdentifierPage, utr).value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing).build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val request = getRequest

        val result = route(application, request).value

        val view = application.injector.instanceOf[BeforeYouContinueView]

        status(result) mustEqual OK

        contentAsString(result) mustEqual view(utr)(using request, messages).toString

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> (s"$logPrefix[onPageLoad]$session" +
            s" relationship does not exist in IV for $utr, sending user to begin journey")
        )
      }

      application.stop()
    }

    "redirect to IvSuccessController for a GET when the relationship already exists" in {
      val answers = emptyUserAnswers.set(IdentifierPage, utr).value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFound).build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.IvSuccessController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> (s"$logPrefix[onPageLoad]$session" +
            s" relationship is already established in IV for $utr, sending user to successfully claimed")
        )
      }

      application.stop()
    }

    "redirect to relationship establishment for a POST" in {
      val fakeNavigator = new FakeNavigator(Call("GET", "/foo"))

      val connector = mock[TrustsStoreConnector]

      when(
        connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using
          any(),
          any(),
          any()
        )
      )
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

      val answers = emptyUserAnswers
        .set(IdentifierPage, utr)
        .value
        .set(IsAgentManagingTrustPage, true)
        .value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
        .overrides(bind[TrustsStoreConnector].toInstance(connector))
        .overrides(bind[Navigator].toInstance(fakeNavigator))
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, postRequest).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value must include(utr)

        verify(connector)
          .claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using any(), any(), any())

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> (s"$logPrefix[onRelationshipNotFound]$session" +
            s" saved users $utr in trusts-store so they can be identified when they" +
            " return from Trust IV. Sending the user into Trust IV to answer questions")
        )
      }

      application.stop()
    }

    "redirect to session expired for a GET" when {
      "data does not exist" in {
        val application =
          applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

          logMessagesWithLevel(logs) mustBe List(noDataError("onPageLoad"))
        }

        application.stop()
      }
    }

    "redirect to session expired for a POST" when {
      "data does not exist" in {
        val application =
          applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, postRequest).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

          logMessagesWithLevel(logs) mustBe List(noDataError("onSubmit"))
        }

        application.stop()
      }
    }

    "return InternalServerError for a GET" when {
      "the relationship check fails" in {
        val answers = emptyUserAnswers.set(IdentifierPage, utr).value

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceError).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, getRequest).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          logMessagesWithLevel(logs) mustBe List(storeError("onPageLoad"))
        }

        application.stop()
      }
    }

    "redirect to InternalServerError for a POST" when {
      "error while storing user answers" in {
        val answers = emptyUserAnswers
          .set(IdentifierPage, utr)
          .value
          .set(IsAgentManagingTrustPage, true)
          .value

        val connector = mock[TrustsStoreConnector]

        when(
          connector
            .claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(using any(), any(), any())
        )
          .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
          .overrides(bind[TrustsStoreConnector].toInstance(connector))
          .build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, postRequest).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          logMessagesWithLevel(logs) mustBe List(storeError("onSubmit"))
        }

        application.stop()
      }

      "relationship is already established and user is redirected to successfully claimed" in {
        val answers = emptyUserAnswers
          .set(IdentifierPage, utr)
          .value
          .set(IsAgentManagingTrustPage, true)
          .value

        val connector = mock[TrustsStoreConnector]

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFound)
          .overrides(bind[TrustsStoreConnector].toInstance(connector))
          .build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, postRequest).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustBe routes.IvSuccessController.onPageLoad.url

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> (s"$logPrefix[handleRelationshipStatus]$session" +
              s" relationship is already established in IV for $utr sending user to successfully claimed")
          )
        }

        application.stop()
      }
    }
  }

}
