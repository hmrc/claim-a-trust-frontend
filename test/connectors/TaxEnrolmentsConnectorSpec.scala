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
import config.FrontendAppConfig
import errors.{ServerError, UpstreamTaxEnrolmentsError}
import models.{EnrolmentCreated, TaxEnrolmentsRequest}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.{Application, Logger}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentsConnectorSpec
    extends AnyWordSpec
    with Matchers
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with LogCapturing
    with LogHelper {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val config: FrontendAppConfig         = app.injector.instanceOf[FrontendAppConfig]
  lazy val connector: TaxEnrolmentsConnector = app.injector.instanceOf[TaxEnrolmentsConnector]

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.tax-enrolments.port" -> server.port(), "auditing.enabled" -> false)
    .build()

  lazy val taxableEnrolmentUrl: String    = "/tax-enrolments/service/HMRC-TERS-ORG/enrolment"
  lazy val nonTaxableEnrolmentUrl: String = "/tax-enrolments/service/HMRC-TERSNT-ORG/enrolment"

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val taxableRequest: String = Json.stringify(
    Json.obj(
      "identifiers" -> Json.arr(Json.obj("key" -> "SAUTR", "value" -> utr)),
      "verifiers"   -> Json.arr(Json.obj("key" -> "SAUTR1", "value" -> utr))
    )
  )

  val nonTaxableRequest: String = Json.stringify(
    Json.obj(
      "identifiers" -> Json.arr(Json.obj("key" -> "URN", "value" -> urn)),
      "verifiers"   -> Json.arr(Json.obj("key" -> "URN1", "value" -> urn))
    )
  )

  private val connectorLogger: Logger = Logger(classOf[TaxEnrolmentsConnector])

  private def wiremock(url: String, payload: String, expectedStatus: Int, mockResponseBody: String = ""): Any =
    server.stubFor(
      put(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(payload))
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(mockResponseBody)
        )
    )

  private def wiremockFault(url: String, payload: String, fault: Fault): Any =
    server.stubFor(
      put(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(payload))
        .willReturn(aResponse().withFault(fault))
    )

  private def noBodyWarning(status: Int): String =
    s"[TaxEnrolmentsConnector][enrol] Received HTTP response code: $status with no message or response body."

  "TaxEnrolmentsConnector" when {

    "taxable" must {

      "returns 204 NO_CONTENT" in {
        wiremock(url = taxableEnrolmentUrl, payload = taxableRequest, expectedStatus = NO_CONTENT)

        connector.enrol(TaxEnrolmentsRequest(utr)).value.futureValue mustBe Right(EnrolmentCreated)
      }

      "returns 400 BAD_REQUEST" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(
            url = taxableEnrolmentUrl,
            payload = taxableRequest,
            expectedStatus = BAD_REQUEST,
            """{"code":"INVALID_CREDENTIAL_ID", "message":"Invalid credential ID given"}"""
          )

          connector.enrol(TaxEnrolmentsRequest(utr)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError("HTTP response 400 INVALID_CREDENTIAL_ID: Invalid credential ID given")
          )

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> ("[TaxEnrolmentsConnector][enrol] Received HTTP response code 400 " +
              "with error code: INVALID_CREDENTIAL_ID and message: Invalid credential ID given")
          )
        }

      "returns 401 UNAUTHORIZED" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(url = taxableEnrolmentUrl, payload = taxableRequest, expectedStatus = UNAUTHORIZED)

          connector.enrol(TaxEnrolmentsRequest(utr)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError("HTTP 401: no message or response body")
          )

          logMessagesWithLevel(logs) mustBe List(Level.WARN -> noBodyWarning(401))
        }

      "fails with a connection reset" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremockFault(taxableEnrolmentUrl, taxableRequest, Fault.CONNECTION_RESET_BY_PEER)

          connector.enrol(TaxEnrolmentsRequest(utr)).value.futureValue match {
            case Left(ServerError(message)) =>
              message must include(taxableEnrolmentUrl)
              message must include("with exception")
            case other                      =>
              fail(s"Expected Left(ServerError), got $other")
          }

          logs.map(_.getLevel) mustBe List(Level.ERROR)
          logs.head.getMessage   must startWith("[TaxEnrolmentsConnector][enrol] Exception thrown with message")
        }
    }

    "non-taxable" must {

      "returns 204 NO_CONTENT" in {
        wiremock(url = nonTaxableEnrolmentUrl, payload = nonTaxableRequest, expectedStatus = NO_CONTENT)

        connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue mustBe Right(EnrolmentCreated)
      }

      "returns 400 BAD_REQUEST" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(url = nonTaxableEnrolmentUrl, payload = nonTaxableRequest, expectedStatus = BAD_REQUEST)

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError("HTTP 400: no message or response body")
          )

          logMessagesWithLevel(logs) mustBe List(Level.WARN -> noBodyWarning(400))
        }

      "returns 401 UNAUTHORIZED" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(url = nonTaxableEnrolmentUrl, payload = nonTaxableRequest, expectedStatus = UNAUTHORIZED)

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError("HTTP 401: no message or response body")
          )

          logMessagesWithLevel(logs) mustBe List(Level.WARN -> noBodyWarning(401))
        }

      "returns 400 with error message" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(
            nonTaxableEnrolmentUrl,
            nonTaxableRequest,
            BAD_REQUEST,
            """{"code":"INVALID_IDENTIFIERS", "message":"Enrolment identifiers not valid innit"}"""
          )

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError("HTTP response 400 INVALID_IDENTIFIERS: Enrolment identifiers not valid innit")
          )

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> ("[TaxEnrolmentsConnector][enrol] Received HTTP response code 400 " +
              "with error code: INVALID_IDENTIFIERS and message: Enrolment identifiers not valid innit")
          )
        }

      "returns 400 with multiple errors" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(
            nonTaxableEnrolmentUrl,
            nonTaxableRequest,
            BAD_REQUEST,
            """{"code":"MULTIPLE_ERRORS", "message":"Multiple errors have occurred", "errors":[
              | {"code": "MULTIPLE_ENROLMENTS_INVALID", "message": "Multiple Enrolments are not valid for this service"},
              | {"code": "INVALID_IDENTIFIERS", "message": "The enrolment identifiers provided were invalid"}
              | ]}""".stripMargin
          )

          val expectedErrors =
            "MULTIPLE_ENROLMENTS_INVALID: Multiple Enrolments are not valid for this service, " +
              "INVALID_IDENTIFIERS: The enrolment identifiers provided were invalid"

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue mustBe Left(
            UpstreamTaxEnrolmentsError(s"HTTP response 400 MULTIPLE_ERRORS: $expectedErrors")
          )

          logMessagesWithLevel(logs) mustBe List(
            Level.WARN -> s"[TaxEnrolmentsConnector][enrol] Received HTTP response code 400 with multiple errors: $expectedErrors"
          )
        }

      "fails with a connection reset" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremockFault(nonTaxableEnrolmentUrl, nonTaxableRequest, Fault.CONNECTION_RESET_BY_PEER)

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue match {
            case Left(ServerError(message)) =>
              message must include(nonTaxableEnrolmentUrl)
              message must include("with exception")
            case other                      =>
              fail(s"Expected Left(ServerError), got $other")
          }

          logs.map(_.getLevel) mustBe List(Level.ERROR)
          logs.head.getMessage   must startWith("[TaxEnrolmentsConnector][enrol] Exception thrown with message")
        }

      "returns 400 with a body that is not valid JSON" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          wiremock(nonTaxableEnrolmentUrl, nonTaxableRequest, BAD_REQUEST, "not json")

          connector.enrol(TaxEnrolmentsRequest(urn)).value.futureValue match {
            case Left(ServerError(message)) => message must include(nonTaxableEnrolmentUrl)
            case other                      => fail(s"Expected Left(ServerError), got $other")
          }

          logs.map(_.getLevel) mustBe List(Level.ERROR)
          logs.head.getMessage   must startWith("[TaxEnrolmentsConnector][enrol] Exception thrown with message")
        }
    }
  }

}
