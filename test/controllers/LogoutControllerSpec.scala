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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{ArgumentCaptor, MockitoSugar}
import pages.IdentifierPage
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class LogoutControllerSpec extends SpecBase with MockitoSugar {

  "logout should redirect to feedback and audit with a utr" in {

    val mockAuditConnector = mock[AuditConnector]

    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])

    val userAnswers = emptyUserAnswers.set(IdentifierPage, "1234567890").success.value

    val application = applicationBuilder(userAnswers = Some(userAnswers))
    .overrides(bind[AuditConnector].toInstance(mockAuditConnector))
    .build()

    val request = FakeRequest(GET, routes.LogoutController.logout().url)

    val result = route(application, request).value

    status(result) mustEqual SEE_OTHER

    redirectLocation(result).value mustBe frontendAppConfig.logoutUrl

    verify(mockAuditConnector, atLeastOnce)
      .sendExplicitAudit(eqTo("trusts"), captor.capture())(any(), any())

    captor.getValue.keys must contain("utr")

    application.stop()

  }

  "logout should redirect to feedback and audit with a urn" in {

    val mockAuditConnector = mock[AuditConnector]

    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])

    val userAnswers = emptyUserAnswers.set(IdentifierPage, "ABTRUST12345678").success.value

    val application = applicationBuilder(userAnswers = Some(userAnswers))
      .overrides(bind[AuditConnector].toInstance(mockAuditConnector))
      .build()

    val request = FakeRequest(GET, routes.LogoutController.logout().url)

    val result = route(application, request).value

    status(result) mustEqual SEE_OTHER

    redirectLocation(result).value mustBe frontendAppConfig.logoutUrl

    verify(mockAuditConnector, atLeastOnce)
      .sendExplicitAudit(eqTo("trusts"), captor.capture())(any(), any())

    captor.getValue.keys must contain("urn")

    application.stop()

  }


}
