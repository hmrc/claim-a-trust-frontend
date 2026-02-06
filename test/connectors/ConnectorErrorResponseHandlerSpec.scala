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

package connectors

import errors.ServerError
import org.scalatest.RecoverMethods
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder

class ConnectorErrorResponseHandlerSpec extends AnyWordSpec with Matchers with RecoverMethods {

  object TestConnectorErrorResponseHandler extends ConnectorErrorResponseHandler {
    val className = "TestConnectorErrorResponseHandler"
  }

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Seq("auditing.enabled" -> false): _*)
    .build()

  "ConnectorErrorResponseHandler" must {

    "return 404" which {

      "returns a ServerError object with the correct message" in {

        val result = ServerError(s"HTTP response $NOT_FOUND for ")

        val actualOutput = TestConnectorErrorResponseHandler.handleError(NOT_FOUND, "", "")

        actualOutput mustBe result
      }
    }
  }

}
