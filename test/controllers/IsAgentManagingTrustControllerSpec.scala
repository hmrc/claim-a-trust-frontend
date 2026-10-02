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
import errors.{ServerError, TrustErrors}
import forms.IsAgentManagingTrustFormProvider
import models.{NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logger
import play.api.data.Form
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.*
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import views.html.IsAgentManagingTrustView

import scala.concurrent.Future

class IsAgentManagingTrustControllerSpec extends SpecBase with MockitoSugar with EitherValues with LogCapturing {

  def onwardRoute: Call = Call("GET", "/foo")

  val formProvider        = new IsAgentManagingTrustFormProvider()
  val form: Form[Boolean] = formProvider()

  val identifier = "0987654321"

  lazy val isAgentManagingTrustRoute: String = routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

  val fakeEstablishmentServiceNotFound = new FakeRelationshipEstablishmentService(Right(RelationshipNotFound))
  val fakeEstablishmentServiceFound    = new FakeRelationshipEstablishmentService(Right(RelationshipFound))
  val fakeEstablishmentServiceFailing  = new FakeRelationshipEstablishmentService(Left(ServerError()))

  private val controllerLogger: Logger = Logger(classOf[IsAgentManagingTrustController])

  private val sessionId = "session-12345"

  private def logPrefix(functionName: String): String =
    s"[IsAgentManagingTrustController][$functionName][Session ID: $sessionId]"

  private def getRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, isAgentManagingTrustRoute).withSession(SessionKeys.sessionId -> sessionId)

  private def postRequest(value: String): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(POST, isAgentManagingTrustRoute)
      .withSession(SessionKeys.sessionId -> sessionId)
      .withFormUrlEncodedBody(("value", value))

  private val agentManagingTrustUserAnswers = UserAnswers(userAnswersId)
    .set(IsAgentManagingTrustPage, true)
    .value
    .set(IdentifierPage, identifier)
    .value

  "IsAgentManagingTrust Controller" must {

    "return OK and the correct view for a GET" in {
      val mockService = mock[RelationshipEstablishment]

      when(mockService.check(any(), any())(using any()))
        .thenReturn(
          EitherT[Future, TrustErrors, RelationEstablishmentStatus](Future.successful(Right(RelationshipNotFound)))
        )

      val userAnswers = emptyUserAnswers
        .set(IdentifierPage, identifier)
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers), relationshipEstablishment = mockService).build()

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      contentAsString(result) must include(messages("isAgentManagingTrustYesNo.title"))

      application.stop()
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val application = applicationBuilder(
        userAnswers = Some(agentManagingTrustUserAnswers),
        relationshipEstablishment = fakeEstablishmentServiceNotFound
      ).build()

      val request = getRequest

      val view = application.injector.instanceOf[IsAgentManagingTrustView]

      val result = route(application, request).value

      status(result) mustEqual OK

      contentAsString(result) mustEqual view(form.fill(true), NormalMode, identifier)(using request, messages).toString

      application.stop()
    }

    "send user to Relationship found" in {
      val application =
        applicationBuilder(
          userAnswers = Some(agentManagingTrustUserAnswers),
          relationshipEstablishment = fakeEstablishmentServiceFound
        )
          .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(routes.IvSuccessController.onPageLoad.url)

        logMessagesWithLevel(logs) mustBe List(
          Level.INFO -> (s"${logPrefix("onPageLoad")}" +
            s" user has recently passed IV for $identifier, sending user to successfully claimed")
        )
      }

      application.stop()
    }

    "return an internal server error onPageload fails" in {

      val application =
        applicationBuilder(
          userAnswers = Some(agentManagingTrustUserAnswers),
          relationshipEstablishment = fakeEstablishmentServiceFailing
        )
          .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest).value

        status(result) mustEqual INTERNAL_SERVER_ERROR
        contentType(result) mustBe Some("text/html")

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("onPageLoad")} Error while loading page"
        )
      }

      application.stop()
    }

    "return an internal server error onSubmit fails" in {
      val repository = mock[SessionRepository]

      when(repository.set(any()))
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

      val application = applicationBuilder(
        userAnswers = Some(emptyUserAnswers),
        relationshipEstablishment = fakeEstablishmentServiceNotFound
      )
        .overrides(
          bind[SessionRepository].toInstance(repository)
        )
        .build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, postRequest("true")).value

        status(result) mustBe INTERNAL_SERVER_ERROR

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("onSubmit")} Error while storing user answers"
        )
      }

      application.stop()
    }

    "redirect to the next page when valid data is submitted" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any()))
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

      val application = applicationBuilder(
        userAnswers = Some(emptyUserAnswers),
        relationshipEstablishment = fakeEstablishmentServiceNotFound
      )
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      val result = route(application, postRequest("true")).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      val userAnswers = emptyUserAnswers
        .set(IdentifierPage, identifier)
        .value

      val application = applicationBuilder(
        userAnswers = Some(userAnswers),
        relationshipEstablishment = fakeEstablishmentServiceNotFound
      )
        .build()

      val request = postRequest("")

      val boundForm = form.bind(Map("value" -> ""))

      val view = application.injector.instanceOf[IsAgentManagingTrustView]

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      contentAsString(result) mustEqual view(boundForm, NormalMode, identifier)(using request, messages).toString

      application.stop()
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceNotFound).build()

      val result = route(application, getRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

      application.stop()
    }

    "redirect to Session Expired for a GET if identifier is not found" in {
      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceNotFound).build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, getRequest).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("onPageLoad")} unable to retrieve identifier from user answers"
        )
      }

      application.stop()
    }

    "redirect to Session Expired for a POST if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceNotFound).build()

      val result = route(application, postRequest("true")).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

      application.stop()
    }

    "redirect to Session Expired for a POST if identifier is not found" in {
      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceNotFound).build()

      withCaptureOfLoggingFrom(controllerLogger) { logs =>
        val result = route(application, postRequest("random")).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

        logMessagesWithLevel(logs) mustBe List(
          Level.WARN -> s"${logPrefix("onSubmit")} unable to retrieve identifier from user answers"
        )
      }

      application.stop()
    }
  }

}
