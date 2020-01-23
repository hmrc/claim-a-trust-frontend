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

package controllers.actions

import config.FrontendAppConfig
import controllers.routes
import play.api.Logger
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.auth.core.{AuthorisationException, NoActiveSession}

import scala.concurrent.{ExecutionContext, Future}

trait AuthPartialFunctions {

  def recoverFromException()(implicit executor: ExecutionContext, config : FrontendAppConfig) : PartialFunction[Throwable, Future[Result]] = {
    case _: NoActiveSession =>
      Logger.info(s"[AuthenticatedIdentifierAction] no active session for user")
      Future.successful(Redirect(config.loginUrl, Map("continue" -> Seq(config.loginContinueUrl))))
    case _: AuthorisationException =>
      Logger.info(s"[AuthenticatedIdentifierAction] exception thrown when authorising")
      Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
  }

}