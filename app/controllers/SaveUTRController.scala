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

package controllers

import controllers.actions.{DataRetrievalAction, IdentifierAction}
import javax.inject.Inject
import models.{NormalMode, UserAnswers}
import pages.UtrPage
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SessionRepository
import services.RelationshipEstablishment
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SaveUTRController @Inject()(
                                   identify: IdentifierAction,
                                   val cc: ControllerComponents,
                                   getData: DataRetrievalAction,
                                   sessionRepository: SessionRepository,
                                   relationship: RelationshipEstablishment
                                 )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def save(utr: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      relationship.check(request.internalId, utr) {
        _ =>
          val userAnswers = request.userAnswers match {
            case Some(userAnswers) => userAnswers.set(UtrPage, utr)
            case _ =>
              UserAnswers(request.internalId).set(UtrPage, utr)
          }
          for {
            updatedAnswers <- Future.fromTry(userAnswers)
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode))
      }

  }
}
