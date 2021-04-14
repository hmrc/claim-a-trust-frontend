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

import connectors.{RelationshipEstablishmentConnector, TrustsStoreConnector}
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import javax.inject.Inject
import models.RelationshipEstablishmentStatus.{UnsupportedRelationshipStatus, UpstreamRelationshipError}
import models.requests.DataRequest
import models.{RelationshipEstablishmentStatus, TrustsStoreRequest}
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.Session
import views.html.{TrustLocked, TrustNotFound, TrustStillProcessing}

import scala.concurrent.{ExecutionContext, Future}
import models.auditing.Events._
import models.auditing.FailureReasons

class IvFailureController @Inject()(
                                     val controllerComponents: MessagesControllerComponents,
                                     lockedView: TrustLocked,
                                     stillProcessingView: TrustStillProcessing,
                                     notFoundView: TrustNotFound,
                                     identify: IdentifierAction,
                                     getData: DataRetrievalAction,
                                     requireData: DataRequiredAction,
                                     relationshipEstablishmentConnector: RelationshipEstablishmentConnector,
                                     connector: TrustsStoreConnector,
                                     auditService: AuditService
                                   )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  private def renderFailureReason(identifier: String, journeyId: String)(implicit hc : HeaderCarrier, request: DataRequest[_]): Future[Result] = {
    relationshipEstablishmentConnector.journeyId(journeyId) map {
      case RelationshipEstablishmentStatus.Locked =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $identifier is locked")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.LOCKED)
        Redirect(routes.IvFailureController.trustLocked())
      case RelationshipEstablishmentStatus.NotFound =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $identifier was not found")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.IDENTIFIER_NOT_FOUND)
        Redirect(routes.IvFailureController.trustNotFound())
      case RelationshipEstablishmentStatus.InProcessing =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $identifier is processing")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.TRUST_STILL_PROCESSING)
        Redirect(routes.IvFailureController.trustStillProcessing())
      case UnsupportedRelationshipStatus(reason) =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] Unsupported IV failure reason: $reason")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.UNSUPPORTED_RELATIONSHIP_STATUS)
        Redirect(routes.FallbackFailureController.onPageLoad())
      case UpstreamRelationshipError(response) =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] HTTP response: $response")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.UPSTREAM_RELATIONSHIP_ERROR)
        Redirect(routes.FallbackFailureController.onPageLoad())
      case _ =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] No errorKey in HTTP response")
        auditService.auditFailure(CLAIM_A_TRUST_FAILURE, identifier, FailureReasons.IV_TECHNICAL_PROBLEM)
        Redirect(routes.FallbackFailureController.onPageLoad())
    }
  }

  def onTrustIvFailure: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(IdentifierPage) match {
        case Some(identifier) =>
          val queryString = request.getQueryString("journeyId")

          queryString.fold{
            logger.warn(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to retrieve a journeyId to determine the reason")
            Future.successful(Redirect(routes.FallbackFailureController.onPageLoad()))
          }{
            journeyId =>
              renderFailureReason(identifier, journeyId)
          }
        case None =>
          logger.warn(s"[Claiming][Trust IV] unable to retrieve an identifier from mongo")
          Future.successful(Redirect(routes.FallbackFailureController.onPageLoad()))
      }
  }

  def trustLocked : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      (for {
        identifier <- request.userAnswers.get(IdentifierPage)
        isManagedByAgent <- request.userAnswers.get(IsAgentManagingTrustPage)
      } yield {
        connector.claim(TrustsStoreRequest(request.internalId, identifier, isManagedByAgent, trustLocked = true)) map { _ =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] failed IV 3 times, $identifier trust is locked out from IV")
          Ok(lockedView(identifier))
        }
      }) getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to determine if trust is locked out from IV")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }

  def trustNotFound : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(IdentifierPage) map {
        identifier =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] IV was unable to find the trust for $identifier")
          Future.successful(Ok(notFoundView(identifier)))
      } getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] no identifier stored in user answers when informing user the trust was not found")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }

  def trustStillProcessing : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(IdentifierPage) map {
        identifier =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] IV determined the trust $identifier was still processing")
          Future.successful(Ok(stillProcessingView(identifier)))
      } getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] no identifier stored in user answers when informing user trust was still processing")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }
}