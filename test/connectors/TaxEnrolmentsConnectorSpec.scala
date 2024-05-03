/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{EnrolmentCreated, TaxEnrolmentsRequest, UpstreamTaxEnrolmentsError}
import org.scalatest.RecoverMethods
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentsConnectorSpec extends AnyWordSpec with Matchers with WireMockHelper with RecoverMethods {

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


  private def wiremock(payload: String, expectedStatus: Int, url: String) =
    server.stubFor(
      put(urlEqualTo(url))
        .withHeader(CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalTo(payload))
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
        )
    )

  "TaxEnrolmentsConnector" when {

    "taxable" must {

      "returns 204 NO_CONTENT" in {

        wiremock(
          payload = taxableRequest,
          expectedStatus = NO_CONTENT,
          url = taxableEnrolmentUrl
        )

        connector.enrol(TaxEnrolmentsRequest(utr)) map { response =>
          response mustBe Right("A RANDOM STRING")
        }

      }

      "returns 400 BAD_REQUEST" in {

        wiremock(
          payload = taxableRequest,
          expectedStatus = BAD_REQUEST,
          url = taxableEnrolmentUrl
        )

        connector.enrol(TaxEnrolmentsRequest(utr)).value map { response =>
          response mustBe EnrolmentCreated
        }

      }
      "returns 401 UNAUTHORIZED" in {

        wiremock(
          payload = taxableRequest,
          expectedStatus = UNAUTHORIZED,
          url = taxableEnrolmentUrl
        )

        connector.enrol(TaxEnrolmentsRequest(utr)).value map { response =>
          response mustBe Left(UpstreamTaxEnrolmentsError)
        }

      }

      "non-taxable" must {

        "returns 204 NO_CONTENT" in {

          wiremock(
            payload = nonTaxableRequest,
            expectedStatus = NO_CONTENT,
            url = nonTaxableEnrolmentUrl
          )

          connector.enrol(TaxEnrolmentsRequest(urn)) map { response =>
            response mustBe Right(EnrolmentCreated)
          }

        }

        "returns 400 BAD_REQUEST" in {

          wiremock(
            payload = nonTaxableRequest,
            expectedStatus = BAD_REQUEST,
            url = nonTaxableEnrolmentUrl
          )

          connector.enrol(TaxEnrolmentsRequest(urn)).value map { response =>
            response mustBe Left(UpstreamTaxEnrolmentsError)
          }
        }

        "returns 401 UNAUTHORIZED" in {

          wiremock(
            payload = nonTaxableRequest,
            expectedStatus = UNAUTHORIZED,
            url = nonTaxableEnrolmentUrl
          )

          connector.enrol(TaxEnrolmentsRequest(urn)).value map { response =>
            response mustBe Left(UpstreamTaxEnrolmentsError)
          }

        }

      }

    }
  }
}
