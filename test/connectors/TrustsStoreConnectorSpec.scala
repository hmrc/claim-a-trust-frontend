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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import errors.ServerError
import models.TrustsStoreRequest
import org.scalatest.RecoverMethods
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class TrustsStoreConnectorSpec extends AnyWordSpec with Matchers with WireMockHelper with RecoverMethods {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Seq(
      "microservice.services.trusts-store.port" -> server.port(),
      "auditing.enabled" -> false): _*
    )
    .build()

  lazy val connector: TrustsStoreConnector = app.injector.instanceOf[TrustsStoreConnector]

  lazy val url: String = "/trusts-store/claim"

  val utr = "1234567890"
  val internalId = "some-authenticated-internal-id"
  val managedByAgent = true

  val request: TrustsStoreRequest = TrustsStoreRequest(
    internalId = internalId,
    id = utr,
    managedByAgent = managedByAgent,
    trustLocked = false
  )

  private def wiremock(payload: String, expectedStatus: Int, expectedResponse: String) =
    server.stubFor(
      post(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(payload))
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(expectedResponse)
        )
    )

  "TrustsStoreConnector" must {

    "call POST /claim" which {

      "returns 201 CREATED" in {

        val json = Json.stringify(Json.toJson(request))

        val response =
          """{
            |  "id": "a string representing the tax reference to associate with this internalId",
            |  "managedByAgent": "boolean derived from answers in the claim a trust journey"
            |}""".stripMargin


        wiremock(
          payload = json,
          expectedStatus = Status.CREATED,
          expectedResponse = response
        )

        connector.claim(request).value map { response =>
          response mustBe Right(true)
        }

      }

      "returns 400 BAD_REQUEST" in {

        val json = Json.stringify(Json.toJson(request))

        val response =
          """{
            |  "status": "400",
            |  "message": "Unable to parse request body into a TrustClaim"
            |}
            |""".stripMargin

        wiremock(
          payload = json,
          expectedStatus = Status.BAD_REQUEST,
          expectedResponse = response
        )

        recoverToSucceededIf[ServerError] {
          connector.claim(request).value
        }

      }

      "returns 500 INTERNAL_SERVER_ERROR" in {

        val json = Json.stringify(Json.toJson(request))

        val response =
          """{
            |  "status": "500",
            |  "message":  ""unable to store to trusts store""
            |}""".stripMargin

        wiremock(
          payload = json,
          expectedStatus = Status.INTERNAL_SERVER_ERROR,
          expectedResponse = response
        )

        recoverToSucceededIf[ServerError] {
          connector.claim(request).value
        }
      }

    }

  }

}
