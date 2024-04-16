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

import org.scalatest.prop.TableDrivenPropertyChecks._
import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import errors.{ServerError, TrustErrors, UpstreamTaxEnrolmentsError}
import models.{EnrolmentCreated, EnrolmentResponse, TaxEnrolmentsRequest}
import org.scalatest.Inside.inside
import org.scalatest.{EitherValues, RecoverMethods}
import org.scalatest.matchers.must.Matchers
import org.scalatest.prop.TableFor4
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class TaxEnrolmentsConnectorSpec
  extends AnyWordSpec
    with Matchers
    with WireMockHelper
    with RecoverMethods
    with EitherValues {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val config: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val connector: TaxEnrolmentsConnector = app.injector.instanceOf[TaxEnrolmentsConnector]

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Seq(
      "microservice.services.tax-enrolments.port" -> server.port(),
      "auditing.enabled" -> false): _*
    )
    .build()

  lazy val taxableEnrolmentUrl: String = s"/tax-enrolments/service/HMRC-TERS-ORG/enrolment"
  lazy val nonTaxableEnrolmentUrl: String = s"/tax-enrolments/service/HMRC-TERSNT-ORG/enrolment"

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val taxableRequest: String = Json.stringify(Json.obj(
    "identifiers" -> Json.arr(
      Json.obj(
        "key" -> "SAUTR",
        "value" -> utr
      )),
    "verifiers" -> Json.arr(
      Json.obj(
        "key" -> "SAUTR1",
        "value" -> utr
      )
    )
  ))

  val nonTaxableRequest: String = Json.stringify(Json.obj(
    "identifiers" -> Json.arr(
      Json.obj(
        "key" -> "URN",
        "value" -> urn
      )),
    "verifiers" -> Json.arr(
      Json.obj(
        "key" -> "URN1",
        "value" -> urn
      )
    )
  ))

  val expectedErrorMessage = "No content to map due to end-of-input"

  private def wiremock(payload: String, expectedStatus: Int, url: String, body: String = "") = {
    server.stubFor(
      put(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(payload))
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(body)
        )
    )
  }

  private def awaitResult(request: TaxEnrolmentsRequest): Either[TrustErrors, EnrolmentResponse] =
    Await.result(
      connector.enrol(request).value,
      Duration.Inf
    )

  val taxEnrolmentsConnectorTestTable: TableFor4[String, String, String, String] =
    Table(
      ("description", "request", "url", "identifier"),
      ("taxable", taxableRequest, taxableEnrolmentUrl, utr),
      ("non-taxable", nonTaxableRequest, nonTaxableEnrolmentUrl, urn)
    )

  "TaxEnrolmentsConnector" when {
    forAll(taxEnrolmentsConnectorTestTable) {
      (description, request, url, identifier) =>
        description must {
          "catch and log multiple errors when they are thrown by tax-enrolment service" in {

            val responseBody: String =
              """
              {
                "code":"MULTIPLE_ERRORS",
                "message":"Multiple errors have occurred",
                "errors": [
                  { "code": "MULTIPLE_ENROLMENTS_INVALID",
                    "message": "Multiple Enrolments are not valid for this service"
                  },
                  { "code": "INVALID_IDENTIFIERS",
                    "message": "The enrolment identifiers provided were invalid"
                  }
                ]
              }
             """
            wiremock(
              payload = request,
              expectedStatus = BAD_REQUEST,
              url = url,
              body = responseBody
            )

            val result: Either[TrustErrors, EnrolmentResponse] = awaitResult(TaxEnrolmentsRequest(identifier))
            result.left.value mustBe UpstreamTaxEnrolmentsError(s"HTTP response ${responseBody}")
          }

          "returns 204 NO_CONTENT" in {

            wiremock(
              payload = request,
              expectedStatus = NO_CONTENT,
              url = url
            )

            val result: Either[TrustErrors, EnrolmentResponse] = awaitResult(TaxEnrolmentsRequest(identifier))
            result.value mustBe EnrolmentCreated
          }

          "returns 400 BAD_REQUEST" in {

            wiremock(
              payload = request,
              expectedStatus = BAD_REQUEST,
              url = url
            )

            val result: Either[TrustErrors, EnrolmentResponse] = awaitResult(TaxEnrolmentsRequest(identifier))
            val serverError = result.left.value
            serverError mustBe a[ServerError]
            inside(serverError) { case ServerError(e) =>
              e must include(expectedErrorMessage)
            }

          }

          "returns 401 UNAUTHORIZED" in {
            wiremock(
              payload = request,
              expectedStatus = UNAUTHORIZED,
              url = url
            )
            val result: Either[TrustErrors, EnrolmentResponse] = awaitResult(TaxEnrolmentsRequest(identifier))
            val serverError = result.left.value
            serverError mustBe a[ServerError]
            inside(serverError) { case ServerError(e) =>
              e must include(expectedErrorMessage)
            }
          }
        }

    }
  }
}
