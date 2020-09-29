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

import config.FrontendAppConfig
import connectors.TaxEnrolmentsConnector
import controllers.actions._
import handlers.ErrorHandler
import javax.inject.Inject
import models.{NormalMode, TaxEnrolmentsRequest}
import pages.{IsAgentManagingTrustPage, UtrPage}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
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
                                     errorHandler: ErrorHandler
                                   )(implicit ec: ExecutionContext,
                                     val config: FrontendAppConfig)
  extends FrontendBaseController with I18nSupport
                                    with AuthPartialFunctions {

  def onPageLoad(): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(UtrPage).map { utr =>

        def onRelationshipFound = {
          taxEnrolmentsConnector.enrol(TaxEnrolmentsRequest(utr)) map { _ =>

            val isAgentManagingTrust = request.userAnswers.get(IsAgentManagingTrustPage) match {
              case None => false
              case Some(value) => value
            }

            Ok(view(isAgentManagingTrust, utr))

          } recover {
            case _ =>
              Logger.error(s"[TaxEnrolments][error] failed to create enrolment for ${request.internalId} $utr")
              InternalServerError(errorHandler.internalServerErrorTemplate)
          }
        }

        lazy val onRelationshipNotFound = Future.successful(Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode)))

        relationshipEstablishment.check(request.internalId, utr) flatMap {
          case RelationshipFound =>
            onRelationshipFound
          case RelationshipNotFound =>
            onRelationshipNotFound
        }
        
      } getOrElse Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))

  }
}
