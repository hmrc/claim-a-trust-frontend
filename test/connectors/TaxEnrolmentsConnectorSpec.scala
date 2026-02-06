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

import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import errors.{TrustErrors, UpstreamTaxEnrolmentsError}
import models.{EnrolmentCreated, EnrolmentResponse, TaxEnrolmentsRequest}
import org.scalatest.RecoverMethods
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TaxEnrolmentsConnectorSpec extends AnyWordSpec with Matchers with WireMockHelper with RecoverMethods {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val config: FrontendAppConfig         = app.injector.instanceOf[FrontendAppConfig]
  lazy val connector: TaxEnrolmentsConnector = app.injector.instanceOf[TaxEnrolmentsConnector]

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Seq("microservice.services.tax-enrolments.port" -> server.port(), "auditing.enabled" -> false): _*)
    .build()

  lazy val taxableEnrolmentUrl: String    = s"/tax-enrolments/service/HMRC-TERS-ORG/enrolment"
  lazy val nonTaxableEnrolmentUrl: String = s"/tax-enrolments/service/HMRC-TERSNT-ORG/enrolment"

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val taxableRequest: String = Json.stringify(
    Json.obj(
      "identifiers" -> Json.arr(
        Json.obj(
          "key"   -> "SAUTR",
          "value" -> utr
        )
      ),
      "verifiers"   -> Json.arr(
        Json.obj(
          "key"   -> "SAUTR1",
          "value" -> utr
        )
      )
    )
  )

  val nonTaxableRequest: String = Json.stringify(
    Json.obj(
      "identifiers" -> Json.arr(
        Json.obj(
          "key"   -> "URN",
          "value" -> urn
        )
      ),
      "verifiers"   -> Json.arr(
        Json.obj(
          "key"   -> "URN1",
          "value" -> urn
        )
      )
    )
  )

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

  "TaxEnrolmentsConnector" when {

    "taxable" must {

      "returns 204 NO_CONTENT" in {

        wiremock(
          url = taxableEnrolmentUrl,
          payload = taxableRequest,
          expectedStatus = NO_CONTENT
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(utr)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Right(EnrolmentCreated)
      }

      "returns 400 BAD_REQUEST" in {

        wiremock(
          url = taxableEnrolmentUrl,
          payload = taxableRequest,
          expectedStatus = BAD_REQUEST,
          """{"code":"INVALID_CREDENTIAL_ID", "message":"Invalid credential ID given"}"""
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(utr)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(
          UpstreamTaxEnrolmentsError("HTTP response 400 INVALID_CREDENTIAL_ID: Invalid credential ID given")
        )
      }

      "returns 401 UNAUTHORIZED" in {

        wiremock(
          url = taxableEnrolmentUrl,
          payload = taxableRequest,
          expectedStatus = UNAUTHORIZED
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(utr)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(UpstreamTaxEnrolmentsError("HTTP 401: no message or response body"))
      }

    }

    "non-taxable" must {

      "returns 204 NO_CONTENT" in {

        wiremock(
          url = nonTaxableEnrolmentUrl,
          payload = nonTaxableRequest,
          expectedStatus = NO_CONTENT
        )

        val future: Future[Either[TrustErrors, EnrolmentResponse]] = connector.enrol(TaxEnrolmentsRequest(urn)).value
        val result                                                 = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        result mustBe Right(EnrolmentCreated)
      }

      "returns 400 BAD_REQUEST" in {

        wiremock(
          url = nonTaxableEnrolmentUrl,
          payload = nonTaxableRequest,
          expectedStatus = BAD_REQUEST
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(urn)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(UpstreamTaxEnrolmentsError("HTTP 400: no message or response body"))
      }

      "returns 401 UNAUTHORIZED" in {

        wiremock(url = nonTaxableEnrolmentUrl, payload = nonTaxableRequest, expectedStatus = UNAUTHORIZED)

        val future   = connector.enrol(TaxEnrolmentsRequest(urn)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(UpstreamTaxEnrolmentsError("HTTP 401: no message or response body"))
      }

      "returns 400 with error message" in {
        wiremock(
          nonTaxableEnrolmentUrl,
          nonTaxableRequest,
          BAD_REQUEST,
          """{"code":"INVALID_IDENTIFIERS", "message":"Enrolment identifiers not valid innit"}"""
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(urn)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(
          UpstreamTaxEnrolmentsError("HTTP response 400 INVALID_IDENTIFIERS: Enrolment identifiers not valid innit")
        )
      }

      "returns 400 with multiple errors" in {
        wiremock(
          nonTaxableEnrolmentUrl,
          nonTaxableRequest,
          BAD_REQUEST,
          """{"code":"MULTIPLE_ERRORS", "message":"Multiple errors have occurred", "errors":[
            |    {"code": "MULTIPLE_ENROLMENTS_INVALID", "message": "Multiple Enrolments are not valid for this service"},
            |    {"code": "INVALID_IDENTIFIERS", "message": "The enrolment identifiers provided were invalid"}
            |  ]}""".stripMargin
        )

        val future   = connector.enrol(TaxEnrolmentsRequest(urn)).value
        val response = Await.result(future, Duration.create(3, TimeUnit.SECONDS))
        response mustBe Left(
          UpstreamTaxEnrolmentsError(
            "HTTP response 400 MULTIPLE_ERRORS: "
              + "MULTIPLE_ENROLMENTS_INVALID: Multiple Enrolments are not valid for this service, "
              + "INVALID_IDENTIFIERS: The enrolment identifiers provided were invalid"
          )
        )
      }
    }

  }

}
