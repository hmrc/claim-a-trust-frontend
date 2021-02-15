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

package controllers

import controllers.actions._
import forms.IsAgentManagingTrustFormProvider
import javax.inject.Inject
import models.Mode
import navigation.Navigator
import pages.{IsAgentManagingTrustPage, IdentifierPage}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.{RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.Session
import views.html.IsAgentManagingTrustView

import scala.concurrent.{ExecutionContext, Future}

class IsAgentManagingTrustController @Inject()(
                                                override val messagesApi: MessagesApi,
                                                sessionRepository: SessionRepository,
                                                navigator: Navigator,
                                                identify: IdentifierAction,
                                                getData: DataRetrievalAction,
                                                requireData: DataRequiredAction,
                                                formProvider: IsAgentManagingTrustFormProvider,
                                                val controllerComponents: MessagesControllerComponents,
                                                view: IsAgentManagingTrustView,
                                                relationship: RelationshipEstablishment
                                              )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  private val form: Form[Boolean] = formProvider()

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(IdentifierPage) map { identifier =>

        lazy val body = {
            val preparedForm = request.userAnswers.get(IsAgentManagingTrustPage) match {
              case None => form
              case Some(value) => form.fill(value)
            }

            Future.successful(Ok(view(preparedForm, mode, identifier)))
        }

        relationship.check(request.internalId, identifier) flatMap {
          case RelationshipFound =>
            logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}]" +
              s" user has recently passed IV for $identifier, sending user to successfully claimed")

            Future.successful(Redirect(routes.IvSuccessController.onPageLoad()))
          case RelationshipNotFound =>
            body
        }

      } getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to retrieve identifier from user answers")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }

  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      form.bindFromRequest().fold(
        formWithErrors =>
          request.userAnswers.get(IdentifierPage) map { utr =>
            Future.successful(BadRequest(view(formWithErrors, mode, utr)))
          } getOrElse {
            logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to retrieve identifier from user answers")
            Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
          }
        ,
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(IsAgentManagingTrustPage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(navigator.nextPage(IsAgentManagingTrustPage, mode, updatedAnswers))
      )
  }
}
