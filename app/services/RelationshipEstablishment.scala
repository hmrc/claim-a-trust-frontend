/*
 * Copyright 2019 HM Revenue & Customs
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

import config.FrontendAppConfig
import controllers.actions.AuthPartialFunctions
import controllers.routes
import javax.inject.Inject
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class RelationshipEstablishmentService @Inject()(
                                                  val authConnector: AuthConnector
                                                )(
                                                  implicit val config: FrontendAppConfig,
                                                  implicit val executionContext: ExecutionContext
                                                )
  extends RelationshipEstablishment with AuthPartialFunctions {

  def check(internalId: String, utr: String)(body: Request[AnyContent] => Future[Result])
           (implicit request: Request[AnyContent]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    def failedRelationshipPF: PartialFunction[Throwable, Future[Result]] = {
      case FailedRelationship(msg) =>
        // relationship does not exist
        Logger.info(s"Relationship does not exist in Trust IV for user $internalId due to $msg")
        body(request)
    }

    val recoverComposed = failedRelationshipPF orElse recoverFromException

    authorised(Relationship(config.relationshipName, Set(BusinessKey(config.relationshipIdentifier, utr)))) {
      Logger.info(s"Relationship established in Trust IV for user $internalId")
      Future.successful(Redirect(routes.IvSuccessController.onPageLoad()))
    } recoverWith {
      recoverComposed
    }
  }

}

trait RelationshipEstablishment extends AuthorisedFunctions {

  def check(internalId: String, utr: String)(body: Request[AnyContent] => Future[Result])
           (implicit request: Request[AnyContent]): Future[Result]

}