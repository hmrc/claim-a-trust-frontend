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

package controllers.testOnlyDoNotUseInAppConf

import base.SpecBase
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar._
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class TestRelationshipEstablishmentControllerSpec extends SpecBase {

  "TestRelationshipEstablishment controller" must {

    "stub IV relationship for a UTR starting with 1" in {

      val mockConnector = mock[RelationshipEstablishmentConnector]

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind(classOf[RelationshipEstablishmentConnector]).toInstance(mockConnector)
        )
        .build()

      val identifier = "1234567890"

      when(mockConnector.createRelationship(any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

      val request = FakeRequest(
        GET,
        controllers.testOnlyDoNotUseInAppConf.routes.TestRelationshipEstablishmentController.check(identifier).url
      )

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.IvSuccessController.onPageLoad.url

      application.stop()
    }

    "not stub IV relationship for a UTR starting with anything but 1" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .build()

      val identifier = "2234567890"

      val request = FakeRequest(
        GET,
        controllers.testOnlyDoNotUseInAppConf.routes.TestRelationshipEstablishmentController.check(identifier).url
      )

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.CouldNotConfirmIdentityController.onPageLoad.url

      application.stop()
    }

    "stub IV relationship for a URN starting with NT" in {

      val mockConnector = mock[RelationshipEstablishmentConnector]

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind(classOf[RelationshipEstablishmentConnector]).toInstance(mockConnector)
        )
        .build()

      val identifier = "NTTRUST12345678"

      when(mockConnector.createRelationship(any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse.apply(OK, "")))

      val request = FakeRequest(
        GET,
        controllers.testOnlyDoNotUseInAppConf.routes.TestRelationshipEstablishmentController.check(identifier).url
      )

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.IvSuccessController.onPageLoad.url

      application.stop()
    }

    "not stub IV relationship for a URN starting with anything but NT" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .build()

      val identifier = "BBTRUST12345678"

      val request = FakeRequest(
        GET,
        controllers.testOnlyDoNotUseInAppConf.routes.TestRelationshipEstablishmentController.check(identifier).url
      )

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.FallbackFailureController.onPageLoad.url

      application.stop()
    }

  }

}
