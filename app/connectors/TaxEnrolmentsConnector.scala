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

import cats.data.EitherT
import config.FrontendAppConfig
import errors.UpstreamTaxEnrolmentsError
import models.{EnrolmentCreated, EnrolmentResponse, IsUTR, TaxEnrolmentsRequest}
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT}
import play.api.libs.json.{JsDefined, JsLookupResult, JsString, JsUndefined, JsValue, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TaxEnrolmentsConnector @Inject()(http: HttpClient, config : FrontendAppConfig) extends ConnectorErrorResponseHandler {

  override val className: String = getClass.getSimpleName

  val testResponseBody ="""
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
  val testResponse: Future[HttpResponse] = Future.successful(HttpResponse(
    BAD_REQUEST,
    testResponseBody
  ))

  def enrol(request: TaxEnrolmentsRequest)
           (implicit hc : HeaderCarrier, ec : ExecutionContext, writes: Writes[TaxEnrolmentsRequest]): TrustEnvelope[EnrolmentResponse] = EitherT {

    val url: String = if (IsUTR(request.identifier)) {
      s"${config.taxEnrolmentsUrl}/service/${config.taxableEnrolmentServiceName}/enrolment"
    } else {
      s"${config.taxEnrolmentsUrl}/service/${config.nonTaxableEnrolmentServiceName}/enrolment"
    }

    val httpReads = HttpReads.Implicits.readRaw

    def logCodeAndErrors(json: JsValue): Unit = {
      val maybeCode: JsLookupResult = json \ "code"
      val maybeErrors: JsLookupResult = json \ "errors"
      (maybeCode, maybeErrors) match {
        case (JsDefined(code), JsDefined(errors)) =>
          if (code == JsString("MULTIPLE_ERRORS")) {
            logger.info(s"Multiple errors encountered, code: ${code}, errors: ${errors}")
          }
          else {
            logger.info(s"Encountered error, code: ${code}, error: ${errors}")
          }
        case (JsDefined(code), JsUndefined()) =>
          logger.info(s"Encountered error, code: ${code}")
        case (JsUndefined(), JsDefined(error)) =>
          logger.info(s"Encountered error, error: ${error}")
        case (JsUndefined(), JsUndefined()) =>
          logger.info(s"Could not find code or errors in response")
      }
    }
//    http.PUT[JsValue, HttpResponse](url, Json.toJson(request))(implicitly[Writes[JsValue]], httpReads, hc, ec).map(
    testResponse.map(
      (response: HttpResponse) => {
        response.status match {
          case NO_CONTENT => Right(EnrolmentCreated)
          case _ =>
            logCodeAndErrors(response.json)
            Left(UpstreamTaxEnrolmentsError(s"HTTP response ${response.body}"))
        }
      }
    ).recover {
      case ex => Left(handleError(ex, "updateTaskStatus", url))
    }
  }
}
