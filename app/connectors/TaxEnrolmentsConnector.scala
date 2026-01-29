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

import cats.data.EitherT
import config.FrontendAppConfig
import errors.UpstreamTaxEnrolmentsError
import models.{EnrolmentCreated, EnrolmentResponse, IsUTR, TaxEnrolmentsRequest}
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TaxEnrolmentsConnector @Inject() (http: HttpClientV2, config: FrontendAppConfig)
    extends ConnectorErrorResponseHandler {

  override val className: String = getClass.getSimpleName

  def enrol(request: TaxEnrolmentsRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    writes: Writes[TaxEnrolmentsRequest]
  ): TrustEnvelope[EnrolmentResponse] = EitherT {

    val fullUrl: String = determineEnrolAndActivateUrl(request)

    http
      .put(url"$fullUrl")
      .withBody(Json.toJson(request))
      .execute[HttpResponse](HttpReads.Implicits.readRaw, ec)
      .map(response =>
        response.status match {
          case NO_CONTENT => Right(EnrolmentCreated)
          case status     =>
            if (response.body.isEmpty) {
              logger.warn(
                s"[TaxEnrolmentsConnector][enrol] Received HTTP response code: $status with no message or response body."
              )
              Left(UpstreamTaxEnrolmentsError(s"HTTP $status: no message or response body"))
            } else {
              val errorCode: String = (response.json \ "code").asOpt[String].getOrElse("N/A")

              val errorMessageBuilder = new StringBuilder()
              if (errorCode contains "MULTIPLE_ERRORS") {
                val multipleErrors = (response.json \ "errors").get.as[List[Map[String, String]]]
                multipleErrors.foreach(err =>
                  errorMessageBuilder
                    .append(err.getOrElse("code", "N/A"))
                    .append(": ")
                    .append(err.getOrElse("message", "N/A"))
                    .append(", ")
                )
                errorMessageBuilder.setLength(errorMessageBuilder.length() - 2) // chop trailing ", "

                logger.warn(
                  "[TaxEnrolmentsConnector][enrol] Received HTTP response code "
                    + s"$status with multiple errors: $errorMessageBuilder"
                )
              } else {
                val errorMessage = (response.json \ "message").asOpt[String].getOrElse("N/A")
                errorMessageBuilder.append(errorMessage)
                logger.warn(
                  "[TaxEnrolmentsConnector][enrol] Received HTTP response code "
                    + s"$status with error code: $errorCode and message: $errorMessage"
                )
              }
              Left(UpstreamTaxEnrolmentsError(s"HTTP response $status $errorCode: $errorMessageBuilder"))
            }
        }
      )
      .recover { case ex =>
        Left(handleError(ex, "updateTaskStatus", fullUrl))
      }
  }

  /**
   * Determines the URL to use to call the tax-enrolments enrolAndActivate endpoint for the service.
   *
   * @param request The {@code TaxEnrolmentsRequest} used to discern if we have a taxable or non-taxable enrolment
   * @return A URL for the appropriate tax-enrolments endpoint in the form of a String
   */
  private def determineEnrolAndActivateUrl(request: TaxEnrolmentsRequest): String =
    if (IsUTR(request.identifier)) {
      s"${config.taxEnrolmentsUrl}/service/${config.taxableEnrolmentServiceName}/enrolment"
    } else {
      s"${config.taxEnrolmentsUrl}/service/${config.nonTaxableEnrolmentServiceName}/enrolment"
    }

}
