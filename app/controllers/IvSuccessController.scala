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
import connectors.TaxEnrolmentsConnector
import controllers.actions._
import errors.{NoData, ServerError, UpstreamTaxEnrolmentsError}
import handlers.ErrorHandler
import models.auditing.Events._
import models.requests.DataRequest
import models.{NormalMode, TaxEnrolmentsRequest}
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{Session, TrustEnvelope}
import views.html.IvSuccessView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IvSuccessController @Inject()(
                                     override val messagesApi: MessagesApi,
                                     actions: Actions,
                                     val controllerComponents: MessagesControllerComponents,
                                     taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                     view: IvSuccessView,
                                     errorHandler: ErrorHandler,
                                     sessionRepository: SessionRepository,
                                     auditService: AuditService,
                                     relationship: RelationshipEstablishment
                                   )(implicit ec: ExecutionContext,
                                     val config: FrontendAppConfig)
  extends FrontendBaseController with I18nSupport
    with AuthPartialFunctions with Logging {

  private val className = this.getClass.getSimpleName

  def onPageLoad: Action[AnyContent] = actions.authWithData.async { implicit request =>
    val result = for {
      identifier <- TrustEnvelope.fromOption(request.userAnswers.get(IdentifierPage))
      relationshipStatus <- relationship.check(request.internalId, identifier)
      outcome <- TrustEnvelope.fromFuture(relationshipOutcome(identifier, relationshipStatus))
    } yield outcome

    result.value.map {
      case Right(call) => call
      case Left(NoData) => logger.warn(s"[IvSuccessController][onPageLoad][Session ID: ${Session.id(hc)}] no identifier found in user answers," +
          s" unable to continue with enrolling credential and claiming the trust on behalf of the user")
        Redirect(routes.SessionExpiredController.onPageLoad)
      case Left(_) => logger.warn(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}] " +
        s"Error while loading page")
        InternalServerError(errorHandler.internalServerErrorTemplate)
    }
  }

  def relationshipOutcome(identifier: String, relationshipStatus: RelationEstablishmentStatus)
             (implicit request: DataRequest[AnyContent]): Future[Result] = relationshipStatus match {
    case RelationshipFound =>
      logger.info(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}]" +
        s" relationship is already established in IV for $identifier, sending user to successfully claimed")
      onRelationshipFound(identifier)
    case RelationshipNotFound =>
      logger.warn(s"[$className][onPageLoad][Session ID: ${Session.id(hc)}] no relationship found in Trust IV," +
        s"cannot continue with enrolling the credential, sending the user back to the start of Trust IV")
      Future.successful(Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode)))
  }

  private def onRelationshipFound(identifier: String)(implicit request: DataRequestHeader): Future[Result] = {

    val hasEnrolled: Boolean = request.userAnswers.get(HasEnrolled).getOrElse(false)

    if (hasEnrolled) {
      val isAgentManagingTrust: Boolean = request.userAnswers.get(IsAgentManagingTrustPage).getOrElse(false)
      Future.successful(Ok(view(isAgentManagingTrust, identifier)))
    } else {
      val result = for {
        _ <- taxEnrolmentsConnector.enrol(TaxEnrolmentsRequest(identifier)) // Does have an exception
        ua <- TrustEnvelope(request.userAnswers.set(HasEnrolled, true))
        _ <- sessionRepository.set(ua)
        isAgentManagingTrust = request.userAnswers.get(IsAgentManagingTrustPage).getOrElse(false)
        _ = auditService.audit(CLAIM_A_TRUST_SUCCESS, identifier, isAgentManagingTrust)
      } yield {
        logger.info(s"[$className][onRelationshipFound][Session ID: ${Session.id(hc)}] successfully enrolled $identifier to users" +
          s"credential after passing Trust IV, user can now maintain the trust")

        Ok(view(isAgentManagingTrust, identifier))
      }
      result.value.map {
        case Right(call) => call
        case Left(UpstreamTaxEnrolmentsError(exceptionMessage)) if exceptionMessage.nonEmpty =>
          handleError(identifier, exceptionMessage, methodName = "onRelationshipFound", sessionId = {Session.id(hc)})
        case Left(ServerError(exceptionMessage)) if exceptionMessage.nonEmpty =>
          handleError(identifier, exceptionMessage, methodName = "onRelationshipFound", sessionId = {Session.id(hc)})
        case _ => val exceptionMessage = s"Encountered an unexpected issue claiming a trust"
          handleError(identifier, exceptionMessage, methodName = "onRelationshipFound", sessionId = {Session.id(hc)})
      }
    }
  }

  private def handleError(identifier: String, exceptionMessage: String, methodName: String, sessionId: String)
                            (implicit request: DataRequestHeader): Result = {
    auditService.auditFailure(CLAIM_A_TRUST_ERROR, identifier, exceptionMessage)
    for {
      ua <- TrustEnvelope(request.userAnswers.set(HasEnrolled, false))
      _ <- sessionRepository.set(ua)
    } yield()
    logger.error(s"[$className][handleError][Session ID: ${Session.id(hc)}] failed to create enrolment for " +
      s"$identifier with tax-enrolments, users credential has not been updated, user needs to claim again")
    InternalServerError(errorHandler.internalServerErrorTemplate)
  }

  def onSubmit: Action[AnyContent] = actions.authWithData {
    _ =>
      Redirect(config.trustsContinueUrl)
  }
}
