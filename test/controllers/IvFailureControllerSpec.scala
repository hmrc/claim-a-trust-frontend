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
import connectors.RelationshipEstablishmentConnector
import pages.UtrPage
import play.api.test.FakeRequest
import play.api.test.Helpers._
import connectors.{RelationshipEstablishmentConnector, TrustsStoreConnector}
import models.TrustsStoreRequest
import navigation.{FakeNavigator, Navigator}
import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, when}
import pages.{IsAgentManagingTrustPage, UtrPage}
import connectors.RelationshipEstablishmentConnector
import pages.UtrPage
import uk.gov.hmrc.http.HttpResponse
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, _}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.libs.json.{JsValue, Json, Writes}


import scala.concurrent.Future
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.Json
import play.api.inject.bind

import scala.concurrent.Future

class IvFailureControllerSpec extends SpecBase {

  lazy val connector: RelationshipEstablishmentConnector = mock[RelationshipEstablishmentConnector]

  "IvFailure Controller" must {

    "callback-failure route" when {

      "redirect to trust locked page when user fails Trusts IV after multiple attempts" in {

        val jsonWithErrorKey = Json.parse(
          """
            |{
            | "errorKey": "TRUST_LOCKED"
            |}
            |""".stripMargin
        )

        val fakeNavigator = new FakeNavigator(Call("GET", "/foo"))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure().url

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RelationshipEstablishmentConnector].toInstance(connector))
          .overrides(bind[Navigator].toInstance(fakeNavigator))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(Future.successful(jsonWithErrorKey))

        val request = FakeRequest(GET, onIvFailureRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustLocked().url

        application.stop()
      }

      "redirect to trust utr not found page when the utr isnt found" in {

        val jsonWithErrorKey = Json.parse(
          """
            |{
            | "errorKey": "UTR_NOT_FOUND"
            |}
            |""".stripMargin
        )

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector)
          )
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(Future.successful(jsonWithErrorKey))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure().url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyFailure=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustNotFound().url

        application.stop()
      }

      "redirect to trust utr in processing page when the utr is processing" in {

        val jsonWithErrorKey = Json.parse(
          """
            |{
            | "errorKey": "UTR_IN_PROCESSING"
            |}
            |""".stripMargin
        )

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector)
          )
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(Future.successful(jsonWithErrorKey))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure().url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyFailure=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustStillProcessing().url

        application.stop()
      }
    }

    "locked route" when {

      "return OK and the correct view for a GET for locked route" in {

        val fakeNavigator = new FakeNavigator(Call("GET", "/foo"))

        val onLockedRoute = routes.IvFailureController.trustLocked().url
        val utr = "3000000001"
        val managedByAgent = true
        val trustLocked = true

        val connector = mock[TrustsStoreConnector]

        when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED)))

        val answers = emptyUserAnswers
          .set(UtrPage, utr).success.value
          .set(IsAgentManagingTrustPage, true).success.value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(bind[TrustsStoreConnector].toInstance(connector))
          .overrides(bind[Navigator].toInstance(fakeNavigator))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) must include("As you have had 3 unsuccessful tries at accessing this trust, you will need to try again in 30 minutes.")

        verify(connector).claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any())

        application.stop()
      }

      "return OK and the correct view for a GET for not found route" in {

        val onLockedRoute = routes.IvFailureController.trustNotFound().url

        val answers = emptyUserAnswers
          .set(UtrPage, "1234567890").success.value

        val application = applicationBuilder(userAnswers = Some(answers)).build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) must include("The Unique Taxpayer Reference (UTR) entered does not match our records.")

        application.stop()
      }

      "return OK and the correct view for a GET for still processing route" in {

        val onLockedRoute = routes.IvFailureController.trustStillProcessing().url

        val answers = emptyUserAnswers
          .set(UtrPage, "1234567891").success.value

        val application = applicationBuilder(userAnswers = Some(answers)).build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) must include("HMRC is still processing changes recently made to this trust.")

        application.stop()
      }

      "redirect to Session Expired for a GET if no existing data is found" in {

        val application = applicationBuilder(userAnswers = None).build()

        val onLockedRoute = routes.IvFailureController.trustLocked().url

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

        application.stop()
      }

    }

  }
}