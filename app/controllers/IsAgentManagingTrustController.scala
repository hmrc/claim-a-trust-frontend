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

package controllers

import controllers.actions._
import errors.{NoData, TrustFormError}
import forms.IsAgentManagingTrustFormProvider
import handlers.ErrorHandler
import models.Mode
import models.requests.DataRequest
import navigation.Navigator
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services.{RelationEstablishmentStatus, RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{Session, TrustEnvelope}
import views.html.IsAgentManagingTrustView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IsAgentManagingTrustController @Inject()(
                                                override val messagesApi: MessagesApi,
                                                sessionRepository: SessionRepository,
                                                navigator: Navigator,
                                                formProvider: IsAgentManagingTrustFormProvider,
                                                val controllerComponents: MessagesControllerComponents,
                                                view: IsAgentManagingTrustView,
                                                errorHandler: ErrorHandler,
                                                relationship: RelationshipEstablishment,
                                                actions: Actions
                                              )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  private val form: Form[Boolean] = formProvider()

  private val className = getClass.getSimpleName

  def onPageLoad(mode: Mode): Action[AnyContent] = actions.authWithData.async { implicit request =>
    val result = for {
      identifier <- TrustEnvelope.fromOption(request.userAnswers.get(IdentifierPage))
      relationshipStatus <- relationship.check(request.internalId, identifier)
      outcome <- TrustEnvelope(relationshipOutcome(identifier, relationshipStatus, mode))
    } yield outcome
    result.value.flatMap {
      case Right(call) => Future.successful(call)
      case Left(NoData) => logger.warn(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}] unable to retrieve identifier from user answers")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad))
      case Left(_) => logger.warn(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}] " +
        s"Error while loading page")
        errorHandler.internalServerErrorTemplate.map(res => InternalServerError(res))
    }
  }
  def relationshipOutcome(identifier: String, relationStatus: RelationEstablishmentStatus, mode: Mode)
                 (implicit request: DataRequest[AnyContent]): Result = relationStatus match {
    case RelationshipFound =>
      logger.info(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}]" +
        s" user has recently passed IV for $identifier, sending user to successfully claimed")
      Redirect(routes.IvSuccessController.onPageLoad)

    case RelationshipNotFound =>
      val preparedForm = request.userAnswers.get(IsAgentManagingTrustPage) match {
        case None => form
        case Some(value) => form.fill(value)
      }
          Ok(view(preparedForm, mode, identifier))
  }


  def onSubmit(mode: Mode): Action[AnyContent] = actions.authWithData.async { implicit request =>
    val result = for {
      value <- TrustEnvelope(validateForm(mode))
      updatedAnswers <- TrustEnvelope(request.userAnswers.set(IsAgentManagingTrustPage, value))
      _ <- sessionRepository.set(updatedAnswers)
    } yield Redirect(navigator.nextPage(IsAgentManagingTrustPage, mode, updatedAnswers))

    result.value.flatMap {
      case Right(call) => Future.successful(call)
      case Left(TrustFormError(call)) => Future.successful(call)
      case Left(_) =>
        logger.warn(s"[$className][onSubmit][Session ID: ${Session.id(hc)}] " +
          s"Error while storing user answers")
        errorHandler.internalServerErrorTemplate.map(res => InternalServerError(res))
    }
  }

  def validateForm(mode: Mode) (implicit request:DataRequest[AnyContent]): Either[TrustFormError, Boolean] = {
    form.bindFromRequest ().fold (
      formWithErrors => {
        request.userAnswers.get(IdentifierPage) map { utr =>
          Left(TrustFormError(BadRequest(view(formWithErrors, mode, utr))))
        } getOrElse {
          logger.warn(s"[$className][onSubmit][Session ID: ${Session.id(hc)}] unable to retrieve identifier from user answers")
          Left(TrustFormError(Redirect(routes.SessionExpiredController.onPageLoad)))
        }
      }, value => Right(value)
    )
  }
}
