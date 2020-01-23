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

  private def renderFailureReason(utr: String, journeyId: String)(implicit hc : HeaderCarrier) = {
    relationshipEstablishmentConnector.journeyId(journeyId) map {
      case RelationshipEstablishmentStatus.Locked =>
        Logger.info(s"[IvFailure][status] $utr is locked")
        Redirect(routes.IvFailureController.trustLocked())
      case RelationshipEstablishmentStatus.NotFound =>
        Logger.info(s"[IvFailure][status] $utr was not found")
        Redirect(routes.IvFailureController.trustNotFound())
      case RelationshipEstablishmentStatus.InProcessing =>
        Logger.info(s"[IvFailure][status] $utr is processing")
        Redirect(routes.IvFailureController.trustStillProcessing())
      case UnsupportedRelationshipStatus(reason) =>
        Logger.warn(s"[IvFailure][status] Unsupported IV failure reason: $reason")
        Redirect(routes.FallbackFailureController.onPageLoad())
      case UpstreamRelationshipError(response) =>
        Logger.warn(s"[IvFailure][status] HTTP response: $response")
        Redirect(routes.FallbackFailureController.onPageLoad())
    }
  }

  def onTrustIvFailure: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(UtrPage) match {
        case Some(utr) =>
          val queryString = request.getQueryString("journeyId")

          queryString.fold{
            Logger.warn(s"[IVFailureController][onTrustIvFailure] unable to retrieve a journeyId to determine the reason")
            Future.successful(Redirect(routes.FallbackFailureController.onPageLoad()))
          }{
            journeyId =>
              renderFailureReason(utr, journeyId)
          }
        case None =>
          Logger.warn(s"[IVFailureController][onTrustIvFailure] unable to retrieve a UTR")
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
          Ok(lockedView(utr))
        }
      }) getOrElse Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
  }

  def trustNotFound : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(UtrPage) map {
        utr =>
          Future.successful(Ok(notFoundView(utr)))
      } getOrElse Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
  }

  def trustStillProcessing : Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(UtrPage) map {
        utr =>
          Future.successful(Ok(stillProcessingView(utr)))
      } getOrElse Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
  }
}