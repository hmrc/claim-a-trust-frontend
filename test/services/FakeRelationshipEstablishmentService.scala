/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import controllers.actions.FakeAuthConnector
import errors.TrustErrors
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.auth.core.AuthConnector
import utils.TrustEnvelope.TrustEnvelope

import scala.concurrent.Future

class FakeRelationshipEstablishmentService(response: Either[TrustErrors, RelationEstablishmentStatus] = Right(RelationshipFound))
  extends RelationshipEstablishment {

  override def authConnector: AuthConnector = new FakeAuthConnector(Future.successful(()))

  override def check(internalId: String, utr: String)
                    (implicit request: Request[AnyContent]): TrustEnvelope[RelationEstablishmentStatus] =
    EitherT[Future, TrustErrors, RelationEstablishmentStatus](Future.successful(response))

}
