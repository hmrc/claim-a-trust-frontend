/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.TrustsStoreConnector
import controllers.actions._
import javax.inject.Inject
import models.requests.IdentifierRequest
import models.{NormalMode, TrustsStoreRequest}
import navigation.Navigator
import pages.{BeforeYouContinuePage, IsAgentManagingTrustPage, UtrPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.RelationshipEstablishment
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import views.html.BeforeYouContinueView

import scala.concurrent.{ExecutionContext, Future}

class BeforeYouContinueController @Inject()(
                                       override val messagesApi: MessagesApi,
                                       navigator: Navigator,
                                       identify: IdentifierAction,
                                       ivRelationship: RelationshipEstablishment,
                                       getData: DataRetrievalAction,
                                       requireData: DataRequiredAction,
                                       val controllerComponents: MessagesControllerComponents,
                                       view: BeforeYouContinueView,
                                       connector: TrustsStoreConnector
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def onPageLoad: Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      val idRequest : IdentifierRequest[AnyContent] = IdentifierRequest(request, request.internalId)

      request.userAnswers.get(UtrPage) map { utr =>
        ivRelationship.check(utr) { _ =>
            Future.successful(Ok(view(utr)))
        }(idRequest)
      } getOrElse Future.successful(Redirect(routes.SessionExpiredController.onPageLoad()))
  }

  def onSubmit: Action[AnyContent] = (identify andThen getData andThen requireData) {
    implicit request =>
      (for {
        utr <- request.userAnswers.get(UtrPage)
        isManagedByAgent <- request.userAnswers.get(IsAgentManagingTrustPage)
      } yield {
        connector.claim(TrustsStoreRequest(request.internalId, utr, isManagedByAgent))
        Redirect(navigator.nextPage(BeforeYouContinuePage, NormalMode, request.userAnswers))
      }) getOrElse Redirect(routes.SessionExpiredController.onPageLoad())
  }
}
