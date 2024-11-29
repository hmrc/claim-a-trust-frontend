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
import errors.UpstreamRelationshipError
import models.RelationshipEstablishmentStatus.{RelationshipEstablishmentStatus, processRelationshipEstablishmentStatusResponse}
import play.api.http.Status.OK
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class RelationshipEstablishmentConnector @Inject()(http: HttpClientV2, config : FrontendAppConfig) extends ConnectorErrorResponseHandler {

  override val className: String = getClass.getSimpleName

  def journeyId(id: String)(implicit hc : HeaderCarrier, ec : ExecutionContext): TrustEnvelope[RelationshipEstablishmentStatus] = EitherT {
    val fullUrl = s"${config.relationshipEstablishmentUrl}/journey-failure/$id"

    http.get(url"url").execute[HttpResponse].map{response =>
      response.status match {
      case OK => Right(processRelationshipEstablishmentStatusResponse(response.json))
      case status => logger.warn(s"[RelationshipEstablishmentConnector] [journeyId] Unexpected HTTP response code $status")
        Left(UpstreamRelationshipError(s"Unexpected HTTP response code $status"))
      }
    }.recover {
      case ex => Left(handleError(ex, "journeyId", fullUrl))
    }
  }
}
