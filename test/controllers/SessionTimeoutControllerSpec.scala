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
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.Logger
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class SessionTimeoutControllerSpec extends SpecBase with LogCapturing {

  private lazy val controller: SessionTimeoutController =
    app.injector.instanceOf[SessionTimeoutController]

  private val controllerLogger: Logger = Logger(classOf[SessionTimeoutController])

  private val sessionId = "session-12345"

  private def requestWithSession: Request[AnyContent] =
    FakeRequest().withSession(SessionKeys.sessionId -> sessionId)

  "timeout" should {

    "stay on current page with current session" when {
      "the keep alive method is used" in
        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val res = controller.keepAlive(requestWithSession)

          status(res) mustEqual OK

          session(res).get(SessionKeys.sessionId) mustBe Some(sessionId)

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> (s"[SessionTimeoutController][keepAlive][Session ID: $sessionId]" +
              " user requested to extend the time remaining to complete Trust IV, user has not been signed out")
          )
        }
    }

    "redirect to session expired page new session " when {
      "the timeout method is" in
        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val res = controller.timeout(requestWithSession)

          status(res) mustEqual SEE_OTHER

          redirectLocation(res).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url

          session(res).isEmpty mustBe true

          logMessagesWithLevel(logs) mustBe List(
            Level.INFO -> (s"[SessionTimeoutController][timeout][Session ID: $sessionId]" +
              " user remained inactive on the service, user has been signed out")
          )
        }
    }
  }

}
