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

import connectors.{RelationshipEstablishmentConnector, TrustsStoreConnector}
import controllers.actions.Actions
import errors.{NoData, UpstreamRelationshipError}
import handlers.ErrorHandler
import models.RelationshipEstablishmentStatus.UnsupportedRelationshipStatus
import models.auditing.Events._
import models.auditing.FailureReasons
import models.requests.DataRequest
import models.{RelationshipEstablishmentStatus, TrustsStoreRequest}
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{Session, TrustEnvelope}
import views.html.{TrustLocked, TrustNotFound, TrustStillProcessing}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IvFailureController @Inject()(
                                     val controllerComponents: MessagesControllerComponents,
                                     lockedView: TrustLocked,
                                     stillProcessingView: TrustStillProcessing,
                                     notFoundView: TrustNotFound,
                                     actions: Actions,
                                     relationshipEstablishmentConnector: RelationshipEstablishmentConnector,
                                     connector: TrustsStoreConnector,
                                     auditService: AuditService,
                                     errorHandler: ErrorHandler
                                   )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  private val className = this.getClass.getSimpleName

  private def renderFailureReason(identifier: String, journeyId: String)(implicit hc : HeaderCarrier, request: DataRequest[_]): Future[Result] = {
    relationshipEstablishmentConnector.journeyId(journeyId).value.map {
      case Right(RelationshipEstablishmentStatus.Locked) =>
        logger.info(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] $identifier is locked")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.LOCKED)
        Redirect(routes.IvFailureController.trustLocked)
      case Right(RelationshipEstablishmentStatus.NotFound) =>
        logger.info(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] $identifier was not found")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.IDENTIFIER_NOT_FOUND)
        Redirect(routes.IvFailureController.trustNotFound)
      case Right(RelationshipEstablishmentStatus.InProcessing) =>
        logger.info(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] $identifier is processing")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.TRUST_STILL_PROCESSING)
        Redirect(routes.IvFailureController.trustStillProcessing)
      case Right(UnsupportedRelationshipStatus(reason)) =>
        logger.error(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] Unsupported IV failure reason: $reason")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.UNSUPPORTED_RELATIONSHIP_STATUS)
        Redirect(routes.CouldNotConfirmIdentityController.onPageLoad)
      case Left(UpstreamRelationshipError(response)) =>
        logger.warn(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] HTTP response: $response")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.UPSTREAM_RELATIONSHIP_ERROR)
        Redirect(routes.FallbackFailureController.onPageLoad)
      case _ =>
        logger.warn(s"[IvFailureController][renderFailureReason][Session ID: ${Session.id(hc)}] No errorKey in HTTP response")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.IV_TECHNICAL_PROBLEM_NO_ERROR_KEY)
        Redirect(routes.FallbackFailureController.onPageLoad)
    }
  }

  def onTrustIvFailure: Action[AnyContent] = actions.authWithData.async { implicit request =>

      request.userAnswers.get(IdentifierPage) match {
        case Some(identifier) =>
          val queryString = request.getQueryString("journeyId")

          queryString.fold{
            logger.error(s"[IvFailureController][onTrustIvFailure][Session ID: ${Session.id(hc)}]" +
              s" unable to retrieve a journeyId to determine the reason")
            auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.IV_TECHNICAL_PROBLEM_NO_JOURNEY_ID)
            Future.successful(Redirect(routes.FallbackFailureController.onPageLoad))
          }{
            journeyId =>
              renderFailureReason(identifier, journeyId)
          }
        case None =>
          logger.error(s"[IvFailureController][onTrustIvFailure][Session ID: ${Session.id(hc)}]" +
            s" unable to retrieve an identifier from mongo")
          Future.successful(Redirect(routes.FallbackFailureController.onPageLoad))
      }
  }

  def trustLocked : Action[AnyContent] = actions.authWithData.async { implicit request =>

      val result = for {
        identifier <- TrustEnvelope.fromOption(request.userAnswers.get(IdentifierPage))
        isManagedByAgent <- TrustEnvelope.fromOption(request.userAnswers.get(IsAgentManagingTrustPage))
        _ <- connector.claim(TrustsStoreRequest(request.internalId, identifier, isManagedByAgent, trustLocked = true))
      } yield {
          logger.info(s"[IvFailureController][onTrustIvFailure][Session ID: ${Session.id(hc)}]" +
            s" failed IV 3 times, $identifier trust is locked out from IV")
          Ok(lockedView(identifier))
      }

      result.value.flatMap {
        case Right(call) => Future.successful(call)
        case Left(NoData) => logger.warn(s"[IvFailureController][onTrustIvFailure][Session ID: ${Session.id(hc)}]" +
                  s" unable to determine if trust was locked out from IV")
          Future.successful(Redirect(routes.SessionExpiredController.onPageLoad))
        case Left(_) => logger.warn(s"[$className][onSubmit][Session ID: ${Session.id(hc)}] " +
          s"Error while storing user answers")
          errorHandler.internalServerErrorTemplate.map(res => InternalServerError(res))

      }
  }

  def trustNotFound : Action[AnyContent] = actions.authWithData.async {
    implicit request =>
      request.userAnswers.get(IdentifierPage) map {
        identifier =>
          logger.info(s"[IvFailureController][trustNotFound][Session ID: ${Session.id(hc)}]" +
            s" IV was unable to find the trust for $identifier")
          Future.successful(Ok(notFoundView(identifier)))
      } getOrElse {
        logger.warn(s"[IvFailureController][trustNotFound][Session ID: ${Session.id(hc)}]" +
          s" no identifier stored in user answers when informing user the trust was not found")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad))
      }
  }

  def trustStillProcessing : Action[AnyContent] = actions.authWithData.async {
    implicit request =>
      request.userAnswers.get(IdentifierPage) map {
        identifier =>
          logger.info(s"[IvFailureController][trustStillProcessing][Session ID: ${Session.id(hc)}]" +
            s" IV determined the trust $identifier was still processing")
          Future.successful(Ok(stillProcessingView(identifier)))
      } getOrElse {
        logger.warn(s"[IvFailureController][trustStillProcessing][Session ID: ${Session.id(hc)}]" +
          s" no identifier stored in user answers when informing user trust was still processing")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad))
      }
  }
}
