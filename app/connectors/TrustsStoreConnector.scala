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
import models.TrustsStoreRequest
import play.api.http.Status.CREATED
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TrustsStoreConnector @Inject()(http: HttpClientV2, config: FrontendAppConfig) extends ConnectorErrorResponseHandler {

  override val className: String = getClass.getSimpleName

  val fullUrl: String = config.trustsStoreUrl + "/claim"

  def claim(request: TrustsStoreRequest)
           (implicit hc: HeaderCarrier, ec: ExecutionContext, writes: Writes[TrustsStoreRequest]): TrustEnvelope[Boolean] = EitherT {

    http.post(url"$fullUrl")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map(_.status match {
        case CREATED => Right(true)
        case status => Left(handleError(status, "claim", fullUrl))
      }).recover {
        case ex => Left(handleError(ex, "claim", fullUrl))
      }
  }

}
