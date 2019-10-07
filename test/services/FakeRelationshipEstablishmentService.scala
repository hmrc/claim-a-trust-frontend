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

package services

import controllers.actions.FakeAuthConnector
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.mvc.{AnyContent, Result}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.Future

class FakeRelationshipEstablishmentService extends RelationshipEstablishment {

  override def authConnector: AuthConnector = new FakeAuthConnector(Future.successful())

  override def check(utr: String)(body: IdentifierRequest[AnyContent] => Future[Result])
                    (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    Logger.info("Faking RelationshipEstablishmentService")
    body(request)
  }

}
