/*
 * Copyright 2022 HM Revenue & Customs
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

package models

import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object RelationshipEstablishmentStatus {

  sealed trait RelationshipEstablishmentStatus

  case object Locked extends RelationshipEstablishmentStatus
  case object NotFound extends RelationshipEstablishmentStatus
  case object InProcessing extends RelationshipEstablishmentStatus
  case class UnsupportedRelationshipStatus(reason: String) extends RelationshipEstablishmentStatus
  case object NoRelationshipStatus extends RelationshipEstablishmentStatus
  case class UpstreamRelationshipError(reason: String) extends RelationshipEstablishmentStatus

  import play.api.http.Status._

  implicit lazy val httpReads : HttpReads[RelationshipEstablishmentStatus] = new HttpReads[RelationshipEstablishmentStatus] {
    override def read(method: String, url: String, response: HttpResponse): RelationshipEstablishmentStatus = {
      response.status match {
        case OK =>
          (response.json \ "errorKey").asOpt[String] match {
            case Some("TRUST_LOCKED")       => Locked
            case Some("TRUST_NOT_FOUND")      => NotFound
            case Some("TRUST_IN_PROCESSING")  => InProcessing
            case Some(unsupported)          => UnsupportedRelationshipStatus(unsupported)
            case _                          => NoRelationshipStatus
          }
        case status => UpstreamRelationshipError(s"Unexpected HTTP response code $status")
      }
    }
  }

}
