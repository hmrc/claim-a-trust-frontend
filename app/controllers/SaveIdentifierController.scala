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

package controllers

import controllers.actions.{DataRetrievalAction, IdentifierAction}
import models.requests.OptionalDataRequest
import models.{NormalMode, UserAnswers}
import pages.IdentifierPage
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services.{RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{IdentifierRegex, Session}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SaveIdentifierController @Inject()(
                                          identify: IdentifierAction,
                                          val controllerComponents: MessagesControllerComponents,
                                          getData: DataRetrievalAction,
                                          sessionRepository: SessionRepository,
                                          relationship: RelationshipEstablishment
                                        )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  def save(identifier: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      identifier match {
        case IdentifierRegex.UtrRegex(utr) => checkIfAlreadyHaveIvRelationship(utr)
        case IdentifierRegex.UrnRegex(urn) => checkIfAlreadyHaveIvRelationship(urn)
        case _ =>
          logger.error(s"[SaveIdentifierController][save][Session ID: ${Session.id(hc)}] " +
            s"Identifier provided is not a valid URN or UTR")
          Future.successful(Redirect(routes.FallbackFailureController.onPageLoad))
      }

  }

  private def checkIfAlreadyHaveIvRelationship(identifier: String)(implicit request: OptionalDataRequest[AnyContent]): Future[Result] = {
    relationship.check(request.internalId, identifier) flatMap {
      case RelationshipFound =>
        logger.info(s"[SaveIdentifierController][checkIfAlreadyHaveIvRelationship][Session ID: ${Session.id(hc)}] " +
          s"relationship is already established in IV for $identifier sending user to successfully claimed")

        Future.successful(Redirect(routes.IvSuccessController.onPageLoad))
      case RelationshipNotFound =>
        saveAndContinue(identifier)
    }
  }

  private def saveAndContinue(identifier: String)(implicit request: OptionalDataRequest[AnyContent]): Future[Result] = {
    val userAnswers = request.userAnswers match {
      case Some(userAnswers) =>
        userAnswers.set(IdentifierPage, identifier)
      case _ =>
        UserAnswers(request.internalId).set(IdentifierPage, identifier)
    }
    for {
      updatedAnswers <- Future.fromTry(userAnswers)
      _              <- sessionRepository.set(updatedAnswers)
    } yield {
      logger.info(s"[SaveIdentifierController][saveAndContinue][Session ID: ${Session.id(hc(request))}]" +
        s" user has started the claim a trust journey for $identifier")
      Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode))
    }
  }
}
