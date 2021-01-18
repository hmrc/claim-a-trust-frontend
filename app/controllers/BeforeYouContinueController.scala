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

import config.FrontendAppConfig
import connectors.TrustsStoreConnector
import controllers.actions._
import javax.inject.Inject
import models.TrustsStoreRequest
import pages.{IsAgentManagingTrustPage, UtrPage}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.Session
import views.html.BeforeYouContinueView

import scala.concurrent.{ExecutionContext, Future}

class BeforeYouContinueController @Inject()(
                                             override val messagesApi: MessagesApi,
                                             identify: IdentifierAction,
                                             relationship: RelationshipEstablishment,
                                             getData: DataRetrievalAction,
                                             requireData: DataRequiredAction,
                                             val controllerComponents: MessagesControllerComponents,
                                             view: BeforeYouContinueView,
                                             connector: TrustsStoreConnector
                                           )(implicit ec: ExecutionContext, config: FrontendAppConfig)
  extends FrontendBaseController with I18nSupport with AuthPartialFunctions with Logging {

  def onPageLoad: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      request.userAnswers.get(UtrPage) map { utr =>
        relationship.check(request.internalId, utr) flatMap {
          case RelationshipFound =>
            logger.info(s"[Claiming][Session ID: ${Session.id(hc)}]" +
              s" relationship is already established in IV for utr $utr sending user to successfully claimed")

            Future.successful(Redirect(routes.IvSuccessController.onPageLoad()))
          case RelationshipNotFound =>
            Future.successful(Ok(view(utr)))
        }
      } getOrElse {
        logger.error(s"[Claiming][Session ID: ${Session.id(hc)}] no utr available in user answers, cannot continue with claiming the trust")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }

  def onSubmit: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      (for {
        utr <- request.userAnswers.get(UtrPage)
        isManagedByAgent <- request.userAnswers.get(IsAgentManagingTrustPage)
      } yield {

        def onRelationshipNotFound =  {

          val successRedirect = config.successUrl
          val failureRedirect = config.failureUrl

          val host = config.relationshipEstablishmentFrontendtUrl(utr)

          val queryString: Map[String, Seq[String]] = Map(
            "success" -> Seq(successRedirect),
            "failure" -> Seq(failureRedirect)
          )

          connector.claim(TrustsStoreRequest(request.internalId, utr, isManagedByAgent, trustLocked = false)) map { _ =>
            logger.info(s"[Claiming][Session ID: ${Session.id(hc)}]" +
              s" saved users utr $utr in trusts-store so they can be identified when they" +
              s" return from Trust IV. Sending the user into Trust IV to answer questions")

            Redirect(host, queryString)
          }

        }

        relationship.check(request.internalId, utr) flatMap {
          case RelationshipFound =>
            logger.info(s"[Claiming][Session ID: ${Session.id(hc)}]" +
              s" relationship is already established in IV for utr $utr sending user to successfully claimed")

            Future.successful(Redirect(routes.IvSuccessController.onPageLoad()))
          case RelationshipNotFound =>
            onRelationshipNotFound
        }
      }) getOrElse {
        logger.error(s"[Claiming][Session ID: ${Session.id(hc)}] no utr available in user answers, cannot continue with claiming the trust")
        Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
      }
  }
}
