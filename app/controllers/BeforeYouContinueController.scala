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

import config.FrontendAppConfig
import connectors.TrustsStoreConnector
import controllers.actions._
import errors.NoData
import handlers.ErrorHandler
import models.TrustsStoreRequest
import models.requests.DataRequest
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{RelationEstablishmentStatus, RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TrustEnvelope.TrustEnvelope
import utils.{Session, TrustEnvelope}
import views.html.BeforeYouContinueView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import controllers.actions.Actions

class BeforeYouContinueController @Inject()(
                                             override val messagesApi: MessagesApi,
                                             relationship: RelationshipEstablishment,
                                             val controllerComponents: MessagesControllerComponents,
                                             view: BeforeYouContinueView,
                                             connector: TrustsStoreConnector,
                                             errorHandler: ErrorHandler,
                                             actions: Actions
                                           )(implicit ec: ExecutionContext, config: FrontendAppConfig)
  extends FrontendBaseController with I18nSupport with AuthPartialFunctions with Logging {

  private val className = this.getClass.getSimpleName

  def onPageLoad: Action[AnyContent] = actions.authWithData.async { implicit request =>
      val result = for {
        identifier <- TrustEnvelope.fromOption(request.userAnswers.get(IdentifierPage))
        relationshipStatus <- relationship.check(request.internalId, identifier)
      } yield relationshipStatus match {
        case RelationshipFound =>
          logger.info(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}]" +
            s" relationship is already established in IV for $identifier, sending user to successfully claimed")
          Redirect(routes.IvSuccessController.onPageLoad)
        case RelationshipNotFound =>
          logger.info(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}]" +
            s" relationship does not exist in IV for $identifier, sending user to begin journey")
          Ok(view(identifier))
      }
    handleResult(result, "onPageLoad")
  }

  def onSubmit: Action[AnyContent] = actions.authWithData.async { implicit request =>
    val result = for {
      identifier <- TrustEnvelope.fromOption(request.userAnswers.get(IdentifierPage))
      isManagedByAgent <- TrustEnvelope.fromOption(request.userAnswers.get(IsAgentManagingTrustPage))
      relationshipStatus <- relationship.check(request.internalId, identifier)
      result <- handleRelationshipStatus(relationshipStatus, identifier, isManagedByAgent)
    } yield result
    handleResult(result, "onSubmit")
  }

  private def handleResult(result: TrustEnvelope[Result], functionName: String)
                  (implicit request: DataRequest[AnyContent]): Future[Result] = result.value.map {
      case Right(call) => call
      case Left(NoData) => logger.error(s"[$className][$functionName][Session ID: ${Session.id(hc)}]" +
        s" no identifier available in user answers, cannot continue with claiming the trust")
        Redirect(routes.SessionExpiredController.onPageLoad)
      case Left(_) => logger.warn(s"[$className][$functionName][Session ID: ${Session.id(hc)}] " +
        s"Error while storing user answers")
        InternalServerError(errorHandler.internalServerErrorTemplate)
  }

  private def handleRelationshipStatus(relationshipStatus: RelationEstablishmentStatus, identifier: String, isManagedByAgent: Boolean)
                              (implicit request: DataRequest[AnyContent]): TrustEnvelope[Result] = relationshipStatus match {
    case RelationshipFound =>
      logger.info(s"[$className][handleRelationshipStatus][Session ID: ${Session.id(hc)}]" +
        s" relationship is already established in IV for $identifier sending user to successfully claimed")
      TrustEnvelope(Redirect(routes.IvSuccessController.onPageLoad))
    case RelationshipNotFound =>
      onRelationshipNotFound(identifier, isManagedByAgent)
  }

  private def onRelationshipNotFound(identifier: String, isManagedByAgent: Boolean)(implicit request: DataRequest[AnyContent]): TrustEnvelope[Result] = {

    val successRedirect = config.successUrl
    val failureRedirect = config.failureUrl

    val host = config.relationshipEstablishmentFrontendUrl(identifier)

    val queryString: Map[String, Seq[String]] = Map(
      "success" -> Seq(successRedirect),
      "failure" -> Seq(failureRedirect)
    )

    connector.claim(TrustsStoreRequest(request.internalId, identifier, isManagedByAgent, trustLocked = false)) map { _ =>
      logger.info(s"[$className][onRelationshipNotFound][Session ID: ${Session.id(hc)}]" +
        s" saved users $identifier in trusts-store so they can be identified when they" +
        s" return from Trust IV. Sending the user into Trust IV to answer questions")

      Redirect(host, queryString)
    }

  }
}
