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

import com.github.tomakehurst.wiremock.client.WireMock.*
import ch.qos.logback.classic.Level
import com.github.tomakehurst.wiremock.http.Fault
import config.FrontendAppConfig
import errors.{ServerError, UpstreamRelationshipError}
import models.RelationshipEstablishmentStatus
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.{Application, Logger}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipEstablishmentConnectorSpec
    extends AnyWordSpec with Matchers with WireMockHelper with ScalaFutures with IntegrationPatience with LogCapturing {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val config: FrontendAppConfig                     = app.injector.instanceOf[FrontendAppConfig]
  lazy val connector: RelationshipEstablishmentConnector = app.injector.instanceOf[RelationshipEstablishmentConnector]

  lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Seq("microservice.services.relationship-establishment.port" -> server.port(), "auditing.enabled" -> false)*
    )
    .build()

  val journeyFailure = s"47a8a543-6961-4221-86e8-d22e2c3c91de"
  val url            = s"/relationship-establishment/journey-failure/$journeyFailure"

  private def setupStubGet(expectedJourneyFailureReason: String) =
    server.stubFor(
      get(urlEqualTo(url))
        .willReturn(okJson(expectedJourneyFailureReason))
    )

  private def setupStubGetWithStatus(status: Int) =
    server.stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(status))
    )

  private def setupStubGetWithFault(fault: Fault) =
    server.stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withFault(fault))
    )

  private val connectorLogger: Logger = Logger(classOf[RelationshipEstablishmentConnector])

  def formatErrorReason(reason: String): String = s"""{ "errorKey": "$reason" }"""

  "RelationshipEstablishmentConnector" must {

    "Calling GET /" which {

      "returns 200 OK with a locked response" in {
        setupStubGet(expectedJourneyFailureReason = formatErrorReason("TRUST_LOCKED"))

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(RelationshipEstablishmentStatus.Locked)
      }

      "returns 200 OK with a not found response" in {
        setupStubGet(expectedJourneyFailureReason = formatErrorReason("TRUST_NOT_FOUND"))

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(RelationshipEstablishmentStatus.NotFound)
      }

      "returns 200 OK with an InProcessing response" in {
        setupStubGet(expectedJourneyFailureReason = formatErrorReason("TRUST_IN_PROCESSING"))

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(RelationshipEstablishmentStatus.InProcessing)
      }

      "returns 200 OK with a question tamper response" in {
        setupStubGet(expectedJourneyFailureReason = formatErrorReason("QUESTION_TAMPER"))

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(
          RelationshipEstablishmentStatus.QuestionTamper
        )
      }

      "returns 200 OK with an unsupported status" in {
        setupStubGet(expectedJourneyFailureReason = formatErrorReason("UNSUPPORTED"))

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(
          RelationshipEstablishmentStatus.UnsupportedRelationshipStatus("UNSUPPORTED")
        )
      }

      "returns 200 OK with no errorKey" in {
        setupStubGet(expectedJourneyFailureReason = "{}")

        connector.journeyId(journeyFailure).value.futureValue mustBe Right(
          RelationshipEstablishmentStatus.NoRelationshipStatus
        )
      }

      "returns 404 NOT_FOUND" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          setupStubGetWithStatus(404)

          connector.journeyId(journeyFailure).value.futureValue mustBe Left(
            UpstreamRelationshipError("Unexpected HTTP response code 404")
          )

          logs.map(e => (e.getLevel, e.getMessage)) mustBe List(
            Level.WARN -> "[RelationshipEstablishmentConnector] [journeyId] Unexpected HTTP response code 404"
          )
        }

      "returns 500 INTERNAL_SERVER_ERROR" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          setupStubGetWithStatus(500)

          connector.journeyId(journeyFailure).value.futureValue mustBe Left(
            UpstreamRelationshipError("Unexpected HTTP response code 500")
          )

          logs.map(e => (e.getLevel, e.getMessage)) mustBe List(
            Level.WARN -> "[RelationshipEstablishmentConnector] [journeyId] Unexpected HTTP response code 500"
          )
        }

      "fails with a connection reset" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          setupStubGetWithFault(Fault.CONNECTION_RESET_BY_PEER)

          connector.journeyId(journeyFailure).value.futureValue match {
            case Left(ServerError(message)) =>
              message must include(url)
              message must include("with exception")
            case other                      =>
              fail(s"Expected Left(ServerError), got $other")
          }

          logs.map(_.getLevel) mustBe List(Level.ERROR)
          logs.head.getMessage   must startWith(
            "[RelationshipEstablishmentConnector][journeyId] Exception thrown with message"
          )
        }

      "returns 200 OK with a body that is not valid JSON" in
        withCaptureOfLoggingFrom(connectorLogger) { logs =>
          setupStubGet(expectedJourneyFailureReason = "not json")

          connector.journeyId(journeyFailure).value.futureValue match {
            case Left(ServerError(message)) => message must include(url)
            case other                      => fail(s"Expected Left(ServerError), got $other")
          }

          logs.map(_.getLevel) mustBe List(Level.ERROR)
          logs.head.getMessage   must startWith(
            "[RelationshipEstablishmentConnector][journeyId] Exception thrown with message"
          )
        }
    }
  }

}
