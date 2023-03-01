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
import models.{EnrolmentCreated, IsUTR, TaxEnrolmentsRequest}
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TaxEnrolmentsConnector @Inject()(http: HttpClient, config : FrontendAppConfig) extends ConnectorErrorResponseHandler {

  override val className: String = getClass.getSimpleName

  def enrol(request: TaxEnrolmentsRequest)
           (implicit hc : HeaderCarrier, ec : ExecutionContext, writes: Writes[TaxEnrolmentsRequest]): TrustEnvelope[Boolean] = EitherT {

    val url: String = if (IsUTR(request.identifier)) {
      s"${config.taxEnrolmentsUrl}/service/${config.taxableEnrolmentServiceName}/enrolment"
    } else {
      s"${config.taxEnrolmentsUrl}/service/${config.nonTaxableEnrolmentServiceName}/enrolment"
    }

    http.PUT[JsValue, HttpResponse](url, Json.toJson(request)).map(
      _.status match {
        case NO_CONTENT => Right(EnrolmentCreated)
        case status => Left(handleError(status, "updateTaskStatus", url))
      }
    ).recover {
      case ex => Left(handleError(ex, "updateTaskStatus", url))
    }
  }
}
