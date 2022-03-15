/*
 * Copyright 2022 HM Revenue & Customs
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
import handlers.ErrorHandler
import javax.inject.Inject
import models.requests.DataRequest
import models.{NormalMode, TaxEnrolmentsRequest}
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services.{AuditService, RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import models.auditing.Events._
import utils.Session
import views.html.IvSuccessView

import scala.concurrent.{ExecutionContext, Future}

class IvSuccessController @Inject()(
                                     override val messagesApi: MessagesApi,
                                     identify: IdentifierAction,
                                     getData: DataRetrievalAction,
                                     requireData: DataRequiredAction,
                                     val controllerComponents: MessagesControllerComponents,
                                     relationshipEstablishment: RelationshipEstablishment,
                                     taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                     view: IvSuccessView,
                                     errorHandler: ErrorHandler,
                                     sessionRepository: SessionRepository,
                                     auditService: AuditService
                                   )(implicit ec: ExecutionContext,
                                     val config: FrontendAppConfig)
  extends FrontendBaseController with I18nSupport
    with AuthPartialFunctions with Logging {

  def onPageLoad(): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      request.userAnswers.get(IdentifierPage).map { identifier =>

        relationshipEstablishment.check(request.internalId, identifier) flatMap {
          case RelationshipFound =>
            onRelationshipFound(identifier)
          case RelationshipNotFound =>
            logger.warn(s"[Claiming][Session ID: ${Session.id(hc)}] no relationship found in Trust IV," +
              s"cannot continue with enrolling the credential, sending the user back to the start of Trust IV")

            Future.successful(Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode)))
        }

      } getOrElse {
        logger.warn(s"[Claiming][Session ID: ${Session.id(hc)}] no identifier found in user answers, unable to" +
          s"continue with enrolling credential and claiming the trust on behalf of the user")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }

  }

  private def onRelationshipFound(identifier: String)(implicit request: DataRequest[_]): Future[Result] = {

    val hasEnrolled: Boolean = request.userAnswers.get(HasEnrolled).getOrElse(false)

    if (hasEnrolled) {
      val isAgentManagingTrust: Boolean = request.userAnswers.get(IsAgentManagingTrustPage).getOrElse(false)
      Future.successful(Ok(view(isAgentManagingTrust, identifier)))
    } else {
      (for {
        _ <- taxEnrolmentsConnector.enrol(TaxEnrolmentsRequest(identifier))
        ua <- Future.fromTry(request.userAnswers.set(HasEnrolled, true))
        _ <- sessionRepository.set(ua)
      } yield {
        val isAgentManagingTrust: Boolean = request.userAnswers.get(IsAgentManagingTrustPage).getOrElse(false)
        auditService.audit(CLAIM_A_TRUST_SUCCESS, identifier, isAgentManagingTrust)

        logger.info(s"[Claiming][Session ID: ${Session.id(hc)}] successfully enrolled $identifier to users" +
          s"credential after passing Trust IV, user can now maintain the trust")

        Ok(view(isAgentManagingTrust, identifier))
      }) recoverWith {
        case exc =>
          auditService.auditFailure(CLAIM_A_TRUST_ERROR, identifier, exc.getMessage)
          Future.fromTry(request.userAnswers.set(HasEnrolled, false)).flatMap { ua =>
            sessionRepository.set(ua).map { _ =>
              logger.error(s"[Claiming][Session ID: ${Session.id(hc)}] failed to create enrolment for " +
                s"$identifier with tax-enrolments, users credential has not been updated, user needs to claim again")
              InternalServerError(errorHandler.internalServerErrorTemplate)
            }
          }
      }
    }
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData) {
    implicit request =>
      Redirect(config.trustsContinueUrl)
  }
}
