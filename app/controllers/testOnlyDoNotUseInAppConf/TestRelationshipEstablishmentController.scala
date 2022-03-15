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

package controllers.testOnlyDoNotUseInAppConf

import com.google.inject.Inject
import controllers.actions.IdentifierAction
import models.requests.IdentifierRequest
import play.api.Logging
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{IdentifierRegex, Session}

import scala.concurrent.{ExecutionContext, Future}
/**
 * Test controller and connector to relationship-establishment to set a relationship for a given UTR.
 * This will then enable the service to "succeed" and "fail" an IV check without having to go into TrustIV.
 */
class TestRelationshipEstablishmentController @Inject()(
                                                         override val messagesApi: MessagesApi,
                                                         val controllerComponents: MessagesControllerComponents,
                                                         relationshipEstablishmentConnector: RelationshipEstablishmentConnector,
                                                         identify: IdentifierAction
                                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController with Logging {

  def check(identifier: String): Action[AnyContent] = identify.async {
    implicit request =>

      logger.info("[Claiming] TrustIV is using a test route, you don't want this in production.")

      identifier match {
        case IdentifierRegex.UtrRegex(utr) =>
          if (utr.startsWith("1")) {
            createRelationship(utr)
          } else {
            logger.info(s"[Claiming][Session ID: ${Session.id(hc)}] UTR did not start with '1', failing IV")
            Future.successful(Redirect(controllers.routes.FallbackFailureController.onPageLoad()))
          }
        case IdentifierRegex.UrnRegex(urn) =>
          if (urn.toLowerCase.startsWith("nt")) {
            createRelationship(urn)
          } else {
            logger.info(s"[Claiming][Session ID: ${Session.id(hc)}] URN did not start with 'NT', failing IV")
            Future.successful(Redirect(controllers.routes.FallbackFailureController.onPageLoad()))
          }
        case _ =>
          logger.error(s"[Claiming][Session ID: ${Session.id(hc)}] " +
            s"Identifier provided is not a valid URN or UTR $identifier")
          Future.successful(Redirect(controllers.routes.FallbackFailureController.onPageLoad()))
      }
  }

  private def createRelationship(identifier: String)(implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    relationshipEstablishmentConnector.createRelationship(request.credentials.providerId, identifier) map {
    _ =>
      logger.info(s"[Claiming][Session ID: ${Session.id(hc)}] Stubbed IV relationship for $identifier")
      Redirect(controllers.routes.IvSuccessController.onPageLoad())
  }

}
