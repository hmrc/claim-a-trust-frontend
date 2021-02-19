/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.testOnlyDoNotUseInAppConf

import com.google.inject.Inject
import config.FrontendAppConfig
import models.IsUTR
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class RelationshipEstablishmentConnector @Inject()(
                                                    val httpClient: HttpClient,
                                                    config: FrontendAppConfig
                                                  )(implicit val ec : ExecutionContext) {

  private val relationshipEstablishmentPostUrl: String = s"${config.relationshipEstablishmentBaseUrl}/relationship-establishment/relationship/"

  private def relationshipEstablishmentGetUrl(credId :String): String =
    s"${config.relationshipEstablishmentBaseUrl}/relationship-establishment/relationship/$credId"

  private def relationshipEstablishmentDeleteUrl(credId: String): String =
    s"${config.relationshipEstablishmentBaseUrl}/test/relationship/$credId"

  private def newRelationship(credId: String, identifier: String): Relationship = {
    if (IsUTR(identifier)) {
      Relationship(config.relationshipName, Set(BusinessKey(config.relationshipTaxableIdentifier, identifier)), credId)
    } else {
      Relationship(config.relationshipName, Set(BusinessKey(config.relationshipNonTaxableIdentifier, identifier)), credId)
    }
  }

  def createRelationship(credId: String, identifier: String)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val ttl = config.relationshipTTL
    httpClient.POST[RelationshipJson, HttpResponse](relationshipEstablishmentPostUrl, RelationshipJson(newRelationship(credId, identifier), ttl))
  }

  def getRelationship(credId: String)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] =
    httpClient.GET[HttpResponse](relationshipEstablishmentGetUrl(credId))

  def deleteRelationship(credId: String)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] =
    httpClient.DELETE[HttpResponse](relationshipEstablishmentDeleteUrl(credId))

}
