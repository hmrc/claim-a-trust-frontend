/*
 * Copyright 2020 HM Revenue & Customs
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
import models.{RelationshipEstablishmentStatus, TaxEnrolmentsRequest}
import org.scalatest.{AsyncWordSpec, MustMatchers, RecoverMethods}
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, Upstream4xxResponse}
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipEstablishmentConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper with RecoverMethods {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val config: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val connector: RelationshipEstablishmentConnector = app.injector.instanceOf[RelationshipEstablishmentConnector]

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Seq(
      "microservice.services.relationship-establishment.port" -> server.port(),
      "auditing.enabled" -> false): _*
    )
    .build()

  val journeyFailure = s"47a8a543-6961-4221-86e8-d22e2c3c91de"
  val url = s"/relationship-establishment/journey-failure/$journeyFailure"

  private def wiremock(expectedJourneyFailureReason: String) =
    server.stubFor(get(urlEqualTo(url))
      .willReturn(okJson(expectedJourneyFailureReason)))

  "RelationshipEstablishmentConnector" must {

    "Calling GET /" which {

      "returns 200 OK with a locked response" in {

        val expectedJourneyFailureReason =
          """
            |{
            | "errorKey": "TRUST_LOCKED"
            |}""".stripMargin

        wiremock(
          expectedJourneyFailureReason = expectedJourneyFailureReason
        )

        connector.journeyId(journeyFailure) map { status =>
          status mustBe RelationshipEstablishmentStatus.Locked
        }
      }

      "returns 200 OK with a not found response" in {

        val expectedJourneyFailureReason =
          """
            |{
            | "errorKey": "UTR_NOT_FOUND"
            |}""".stripMargin

        wiremock(
          expectedJourneyFailureReason = expectedJourneyFailureReason
        )

        connector.journeyId(journeyFailure) map { status =>
          status mustBe RelationshipEstablishmentStatus.NotFound
        }
      }

      "returns 200 OK with an InProcessing response" in {

        val expectedJourneyFailureReason =
          """
            |{
            | "errorKey": "UTR_IN_PROCESSING"
            |}""".stripMargin

        wiremock(
          expectedJourneyFailureReason = expectedJourneyFailureReason
        )

        connector.journeyId(journeyFailure) map { status =>
          status mustBe RelationshipEstablishmentStatus.InProcessing
        }
      }

      "returns 200 OK with an unsupported status" in {

        val expectedJourneyFailureReason =
          """
            |{
            | "errorKey": "UNSUPPORTED"
            |}""".stripMargin

        wiremock(
          expectedJourneyFailureReason = expectedJourneyFailureReason
        )

        connector.journeyId(journeyFailure) map { status =>
          status mustBe a[RelationshipEstablishmentStatus.UnsupportedRelationshipStatus]
        }
      }
    }
  }

}