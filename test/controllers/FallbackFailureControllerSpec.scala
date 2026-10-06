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
import play.api.Logger
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class FallbackFailureControllerSpec extends SpecBase with LogCapturing {

  def onFailureRoute: String = routes.FallbackFailureController.onPageLoad.url

  private val controllerLogger: Logger = Logger(classOf[FallbackFailureController])

  private val sessionId = "session-12345"
  private val logPrefix = s"[FallbackFailureController][onPageLoad][Session ID: $sessionId]"

  private def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, onFailureRoute).withSession(SessionKeys.sessionId -> sessionId)

  "FallbackFailure Controller" must {

    "render internal server error view and log an error with the referer" when {
      "a Referer header is present" in {
        val referer = "http://localhost:1234/some-url"

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, request.withHeaders(REFERER -> referer)).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> (s"$logPrefix Trust IV encountered a problem that could not be recovered from." +
              s" referer url: $referer")
          )
        }

        application.stop()
      }
    }

    "render internal server error view and log a warning" when {
      "there is no Referer header" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        withCaptureOfLoggingFrom(controllerLogger) { logs =>
          val result = route(application, request).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> s"$logPrefix Trust IV encountered a problem that could not be recovered from"
          )
        }

        application.stop()
      }
    }
  }

}
