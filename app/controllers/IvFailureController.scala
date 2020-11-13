/*
 * Copyright 2020 HM Revenue & Customs
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
import models.{RelationshipEstablishmentStatus, TrustsStoreRequest}
import pages.{IsAgentManagingTrustPage, UtrPage}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import utils.Session
import views.html.{TrustLocked, TrustNotFound, TrustStillProcessing}

import scala.concurrent.{ExecutionContext, Future}

class IvFailureController @Inject()(
                                     val controllerComponents: MessagesControllerComponents,
                                     lockedView: TrustLocked,
                                     stillProcessingView: TrustStillProcessing,
                                     notFoundView: TrustNotFound,
                                     identify: IdentifierAction,
                                     getData: DataRetrievalAction,
                                     requireData: DataRequiredAction,
                                     relationshipEstablishmentConnector: RelationshipEstablishmentConnector,
                                     connector: TrustsStoreConnector
                                   )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  private val logger = Logger(getClass)

  private def renderFailureReason(utr: String, journeyId: String)(implicit hc : HeaderCarrier) = {
    relationshipEstablishmentConnector.journeyId(journeyId) map {
      case RelationshipEstablishmentStatus.Locked =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $utr is locked")
        Redirect(routes.IvFailureController.trustLocked())
      case RelationshipEstablishmentStatus.NotFound =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $utr was not found")
        Redirect(routes.IvFailureController.trustNotFound())
      case RelationshipEstablishmentStatus.InProcessing =>
        logger.info(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] $utr is processing")
        Redirect(routes.IvFailureController.trustStillProcessing())
      case UnsupportedRelationshipStatus(reason) =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] Unsupported IV failure reason: $reason")
        Redirect(routes.FallbackFailureController.onPageLoad())
      case UpstreamRelationshipError(response) =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] HTTP response: $response")
        Redirect(routes.FallbackFailureController.onPageLoad())
      case _ =>
        logger.warn(s"[Claiming][Trust IV][status][Session ID: ${Session.id(hc)}] No errorKey in HTTP response")
        Redirect(routes.FallbackFailureController.onPageLoad())
    }
  }

  def onTrustIvFailure: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(UtrPage) match {
        case Some(utr) =>
          val queryString = request.getQueryString("journeyId")

          queryString.fold{
            logger.warn(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to retrieve a journeyId to determine the reason")
            Future.successful(Redirect(routes.FallbackFailureController.onPageLoad()))
          }{
            journeyId =>
              renderFailureReason(utr, journeyId)
          }
        case None =>
          logger.warn(s"[Claiming][Trust IV] unable to retrieve a UTR")
          Future.successful(Redirect(routes.FallbackFailureController.onPageLoad()))
      }
  }

  def trustLocked : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      (for {
        utr <- request.userAnswers.get(UtrPage)
        isManagedByAgent <- request.userAnswers.get(IsAgentManagingTrustPage)
      } yield {
        connector.claim(TrustsStoreRequest(request.internalId, utr, isManagedByAgent, trustLocked = true)) map { _ =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] failed IV 3 times, trust is locked out from IV")
          Ok(lockedView(utr))
        }
      }) getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] unable to determine if trust is locked out from IV")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }

  def trustNotFound : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(UtrPage) map {
        utr =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] IV was unable to find the trust for utr $utr")
          Future.successful(Ok(notFoundView(utr)))
      } getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] no utr stored in user answers when informing user the trust was not found")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }

  def trustStillProcessing : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(UtrPage) map {
        utr =>
          logger.info(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] IV determined the trust utr $utr was still processing")
          Future.successful(Ok(stillProcessingView(utr)))
      } getOrElse {
        logger.error(s"[Claiming][Trust IV][Session ID: ${Session.id(hc)}] no utr stored in user answers when informing user trust was still processing")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }
}