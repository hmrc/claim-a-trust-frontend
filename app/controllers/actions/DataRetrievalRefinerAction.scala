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

package controllers.actions

import handlers.ErrorHandler
import models.UserAnswers
import models.requests.{IdentifierRequest, OptionalDataRequest}
import play.api.mvc.Results.InternalServerError
import play.api.mvc.{ActionRefiner, Result}
import repositories.SessionRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DataRetrievalRefinerAction @Inject()(sessionRepository: SessionRepository, errorHandler: ErrorHandler)(implicit val executionContext: ExecutionContext)
  extends ActionRefiner[IdentifierRequest, OptionalDataRequest] {
  override protected def refine[A](request: IdentifierRequest[A]): Future[Either[Result, OptionalDataRequest[A]]] = {
    sessionRepository.get(request.identifier).map { maybeUserAnswers: Option[UserAnswers] =>
      OptionalDataRequest(
        request = request.request,
        internalId = request.identifier,
        credentials = request.credentials,
        affinityGroup = request.affinityGroup,
        userAnswers = maybeUserAnswers
      )
    }.value.map{
      case Right(optData) => Right(optData)
      case Left(_) => Left(InternalServerError(errorHandler.internalServerErrorTemplate(request.request))
      )
    }
  }
}