/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.TrustsStoreConnector
import errors.{ServerError, TrustErrors}
import models.TrustsStoreRequest
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatest.EitherValues
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{FakeRelationshipEstablishmentService, RelationshipFound, RelationshipNotFound}
import views.html.BeforeYouContinueView

import scala.concurrent.Future

class BeforeYouContinueControllerSpec extends SpecBase with EitherValues {

  val utr = "0987654321"
  val managedByAgent = true
  val trustLocked = false

  val fakeEstablishmentServiceFailing = new FakeRelationshipEstablishmentService(Right(RelationshipNotFound))

  val fakeEstablishmentServiceFound = new FakeRelationshipEstablishmentService(Right(RelationshipFound))

  "BeforeYouContinue Controller" must {

    "return OK and the correct view for a GET" in {

      val answers = emptyUserAnswers.set(IdentifierPage, utr).value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing).build()

      val request = FakeRequest(GET, routes.BeforeYouContinueController.onPageLoad.url)

      val result = route(application, request).value

      val view = application.injector.instanceOf[BeforeYouContinueView]

      status(result) mustEqual OK

      contentAsString(result) mustEqual
        view(utr)(request, messages).toString

      application.stop()
    }

    "return OK and the correct view for IvSuccessController" in {

      val answers = emptyUserAnswers.set(IdentifierPage, utr).value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFound).build()

      val request = FakeRequest(GET, routes.BeforeYouContinueController.onPageLoad.url)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe routes.IvSuccessController.onPageLoad.url

      application.stop()
    }

    "redirect to relationship establishment for a POST" in {

      val fakeNavigator = new FakeNavigator(Call("GET", "/foo"))

      val connector = mock[TrustsStoreConnector]

      when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
        .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

      val answers = emptyUserAnswers
        .set(IdentifierPage, "0987654321").value
        .set(IsAgentManagingTrustPage, true).value

      val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
        .overrides(bind[TrustsStoreConnector].toInstance(connector))
        .overrides(bind[Navigator].toInstance(fakeNavigator))
        .build()

      val request = FakeRequest(POST, routes.BeforeYouContinueController.onSubmit.url)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value must include("0987654321")

      verify(connector).claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any())

      application.stop()

    }

    "redirect to session expired for a GET" when {

      "data does not exist" in {
        val answers = emptyUserAnswers

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing).build()

        val request = FakeRequest(GET, routes.BeforeYouContinueController.onPageLoad.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

    }

    "redirect to session expired for a POST" when {

      "data does not exist" in {
        val answers = emptyUserAnswers

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing).build()

        val request = FakeRequest(POST, routes.BeforeYouContinueController.onSubmit.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

    }

    "redirect to InternalServerError for a POST" when {

      "error while storing user answers" in {
        val answers = emptyUserAnswers
          .set(IdentifierPage, "0987654321").value
          .set(IsAgentManagingTrustPage, true).value

        val connector = mock[TrustsStoreConnector]

        when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
          .overrides(bind[TrustsStoreConnector].toInstance(connector))
          .build()

        val request = FakeRequest(POST, routes.BeforeYouContinueController.onSubmit.url)

        val result = route(application, request).value

        status(result) mustEqual INTERNAL_SERVER_ERROR

        contentType(result) mustBe Some("text/html")

        application.stop()
      }

      "relationship is already established and user is redirected to successfully claimed" in {
        val answers = emptyUserAnswers
          .set(IdentifierPage, "0987654321").value
          .set(IsAgentManagingTrustPage, true).value

        val connector = mock[TrustsStoreConnector]

        when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

        val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFound)
          .overrides(bind[TrustsStoreConnector].toInstance(connector))
          .build()

        val request = FakeRequest(POST, routes.BeforeYouContinueController.onSubmit.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.IvSuccessController.onPageLoad.url

        application.stop()
      }
    }
  }
}
