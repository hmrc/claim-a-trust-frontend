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

package services

import cats.data.EitherT
import config.FrontendAppConfig
import errors.ServerError
import models.RelationshipForIdentifier
import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.Session
import utils.TrustEnvelope.TrustEnvelope

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

sealed trait RelationEstablishmentStatus

case object RelationshipFound extends RelationEstablishmentStatus
case object RelationshipNotFound extends RelationEstablishmentStatus
class RelationshipEstablishmentService @Inject()(
                                                  val authConnector: AuthConnector,
                                                  relationshipForIdentifier: RelationshipForIdentifier
                                                )(
                                                  implicit val config: FrontendAppConfig,
                                                  implicit val executionContext: ExecutionContext
                                                )
  extends RelationshipEstablishment with Logging {

  def check(internalId: String, identifier: String)(implicit request: Request[AnyContent]): TrustEnvelope[RelationEstablishmentStatus] = EitherT{

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    logger.info(s"[RelationshipEstablishmentService][check][Session ID: ${Session.id(hc)}]" +
      s"HeaderCarrier contained bearer token: ${hc.authorization.isDefined}"
    )

    val relationshipToCheck = relationshipForIdentifier(identifier)

    authorised(relationshipToCheck) {
      logger.info(s"[RelationshipEstablishmentService][check][Session ID: ${Session.id(hc)}]" +
        s" Relationship established in Trust IV for user and $identifier")
        Future.successful(Right(RelationshipFound))
    } recoverWith {
      case FailedRelationship(msg) =>
        logger.warn(s"[RelationshipEstablishmentService][check][Session ID: ${Session.id(hc)}]" +
          s" Relationship does not exist in Trust IV for user and $identifier due to error $msg")
        Future.successful(Right(RelationshipNotFound))
      case e : Throwable =>
        logger.error(s"[RelationshipEstablishmentService][check][Session ID: ${Session.id(hc)}]" +
          s" Service was unable to determine if an IV relationship existed in Trust IV. Cannot continue with the journey")
        Future.successful(Left(ServerError(e.getMessage)))
    }
  }

}

trait RelationshipEstablishment extends AuthorisedFunctions {

  def check(internalId: String, utr: String)(implicit request: Request[AnyContent]): TrustEnvelope[RelationEstablishmentStatus]

}
