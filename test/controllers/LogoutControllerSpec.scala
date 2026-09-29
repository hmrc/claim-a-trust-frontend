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
import ch.qos.logback.classic.Level
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{atLeastOnce, verify}
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar
import pages.IdentifierPage
import play.api.Logger
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class LogoutControllerSpec extends SpecBase with MockitoSugar with EitherValues with LogCapturing {

  private val controllerLogger: Logger = Logger(classOf[LogoutController])

  private val sessionId = "session-12345"

  private def logoutRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.LogoutController.logout().url).withSession(SessionKeys.sessionId -> sessionId)

  private val signedOutLog: (Level, String) =
    Level.INFO -> s"[LogoutController][logout][Session ID: $sessionId] user signed out from the service"

  "logout should redirect to feedback and audit with a utr" in {
    val mockAuditConnector = mock[AuditConnector]

    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])

    val userAnswers = emptyUserAnswers.set(IdentifierPage, "1234567890").value

    val application = applicationBuilder(userAnswers = Some(userAnswers))
      .overrides(bind[AuditConnector].toInstance(mockAuditConnector))
      .build()

    withCaptureOfLoggingFrom(controllerLogger) { logs =>
      val result = route(application, logoutRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe frontendAppConfig.logoutUrl

      verify(mockAuditConnector, atLeastOnce)
        .sendExplicitAudit(eqTo("trusts"), captor.capture())(using any(), any())

      captor.getValue.get("utr")       mustBe Some("1234567890")
      captor.getValue.get("sessionId") mustBe Some(sessionId)

      logMessagesWithLevel(logs) mustBe List(signedOutLog)
    }

    application.stop()
  }

  "logout should redirect to feedback and audit with a urn" in {
    val mockAuditConnector = mock[AuditConnector]

    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])

    val userAnswers = emptyUserAnswers.set(IdentifierPage, "ABTRUST12345678").value

    val application = applicationBuilder(userAnswers = Some(userAnswers))
      .overrides(bind[AuditConnector].toInstance(mockAuditConnector))
      .build()

    withCaptureOfLoggingFrom(controllerLogger) { logs =>
      val result = route(application, logoutRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe frontendAppConfig.logoutUrl

      verify(mockAuditConnector, atLeastOnce)
        .sendExplicitAudit(eqTo("trusts"), captor.capture())(using any(), any())

      captor.getValue.get("urn")       mustBe Some("ABTRUST12345678")
      captor.getValue.get("sessionId") mustBe Some(sessionId)

      logMessagesWithLevel(logs) mustBe List(signedOutLog)
    }

    application.stop()
  }

}
