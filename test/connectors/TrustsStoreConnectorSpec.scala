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

import base.LogHelper
import ch.qos.logback.classic.Level
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import errors.ServerError
import models.TrustsStoreRequest
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.{Application, Logger}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class TrustsStoreConnectorSpec
    extends AnyWordSpec
    with Matchers
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with LogCapturing
    with LogHelper {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.trusts-store.port" -> server.port(), "auditing.enabled" -> false)
    .build()

  lazy val connector: TrustsStoreConnector = app.injector.instanceOf[TrustsStoreConnector]

  lazy val url: String     = "/trusts-store/claim"
  lazy val fullUrl: String = s"http://localhost:${server.port()}$url"

  val utr            = "1234567890"
  val internalId     = "some-authenticated-internal-id"
  val managedByAgent = true

  val request: TrustsStoreRequest = TrustsStoreRequest(
    internalId = internalId,
    id = utr,
    managedByAgent = managedByAgent,
    trustLocked = false
  )

  val requestJson: String = Json.stringify(Json.toJson(request))

  private val connectorLogger: Logger = Logger(classOf[TrustsStoreConnector])

  private def wiremock(expectedStatus: Int, expectedResponse: String) =
    server.stubFor(
      post(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(requestJson))
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(expectedResponse)
        )
    )

  "TrustsStoreConnector" must {

    "call POST /claim" which {

      "returns 201 CREATED" in {
        val response =
          """{
            |  "id": "a string representing the tax reference to associate with this internalId",
            |  "managedByAgent": "boolean derived from answers in the claim a trust journey"
            |}""".stripMargin

        wiremock(expectedStatus = Status.CREATED, expectedResponse = response)

        connector.claim(request).value.futureValue mustBe Right(true)
      }

      "returns 400 BAD_REQUEST" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          val response =
            """{
              |  "status": "400",
              |  "message": "Unable to parse request body into a TrustClaim"
              |}""".stripMargin

          wiremock(expectedStatus = Status.BAD_REQUEST, expectedResponse = response)

          connector.claim(request).value.futureValue mustBe Left(ServerError(s"HTTP response 400 for $fullUrl"))

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> "[TrustsStoreConnector][claim] Error with status: 400"
          )
        }

      "returns 500 INTERNAL_SERVER_ERROR" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          val response =
            """{
              |  "status": "500",
              |  "message": "unable to store to trusts store"
              |}""".stripMargin

          wiremock(expectedStatus = Status.INTERNAL_SERVER_ERROR, expectedResponse = response)

          connector.claim(request).value.futureValue mustBe Left(ServerError(s"HTTP response 500 for $fullUrl"))

          logMessagesWithLevel(logs) mustBe List(
            Level.ERROR -> "[TrustsStoreConnector][claim] Error with status: 500"
          )
        }

      "fails with a connection reset" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          server.stubFor(
            post(urlEqualTo(url))
              .withRequestBody(equalTo(requestJson))
              .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
          )

          val result = connector.claim(request).value.futureValue

          val logPrefix        = "[TrustsStoreConnector][claim] Exception thrown with message "
          val exceptionMessage = logs.head.getMessage.stripPrefix(logPrefix)

          logMessagesWithLevel(logs) mustBe List(Level.ERROR -> s"$logPrefix$exceptionMessage")
          result                     mustBe Left(ServerError(s"Error occurred when calling $fullUrl with exception $exceptionMessage"))
        }

    }
  }

}
