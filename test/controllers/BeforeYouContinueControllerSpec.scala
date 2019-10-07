/*
 * Copyright 2019 HM Revenue & Customs
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
import connectors.TrustsStoreConnector
import models.TrustsStoreRequest
import navigation.{FakeNavigator, Navigator}
import pages.{IsAgentManagingTrustPage, UtrPage}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.inject.bind
import play.api.mvc.Call
import uk.gov.hmrc.http.HttpResponse
import views.html.BeforeYouContinueView

import scala.concurrent.Future

class BeforeYouContinueControllerSpec extends SpecBase {

  val utr = "0987654321"
  val managedByAgent = true

  "BeforeYouContinue Controller" must {

    "return OK and the correct view for a GET" in {

      val answers = emptyUserAnswers.set(UtrPage, utr).success.value

      val application = applicationBuilder(userAnswers = Some(answers)).build()

      val request = FakeRequest(GET, routes.BeforeYouContinueController.onPageLoad().url)

      val result = route(application, request).value

      val view = application.injector.instanceOf[BeforeYouContinueView]

      status(result) mustEqual OK

      contentAsString(result) mustEqual
        view(utr)(fakeRequest, messages).toString

      application.stop()
    }

    "redirect to relationship establishment for a POST" in {

      val fakeNavigator = new FakeNavigator(Call("GET", "/foo"))

      val connector = mock[TrustsStoreConnector]

      when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent)))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED)))

      val answers = emptyUserAnswers
        .set(UtrPage, "0987654321").success.value
        .set(IsAgentManagingTrustPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(bind[TrustsStoreConnector].toInstance(connector))
        .overrides(bind[Navigator].toInstance(fakeNavigator))
        .build()

      val request = FakeRequest(POST, routes.BeforeYouContinueController.onSubmit().url)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value must include("0987654321")

      verify(connector).claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent)))(any(), any(), any())

      application.stop()

    }
  }
}
