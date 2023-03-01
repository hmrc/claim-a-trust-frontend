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
import repositories.SessionRepository

import scala.concurrent.{ExecutionContext, Future}

class FakeDataRetrievalAction(dataToReturn: Option[UserAnswers], sessionRepository: SessionRepository, errorHandler: ErrorHandler)
                             (implicit executionContext: ExecutionContext)
  extends DataRetrievalRefinerAction(sessionRepository, errorHandler) {
  protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] =
    dataToReturn match {
      case None =>
        Future(OptionalDataRequest(
          request = request.request,
          internalId = request.identifier,
          credentials = request.credentials,
          affinityGroup = request.affinityGroup,
          userAnswers = None))
      case Some(userAnswers) =>
        Future(OptionalDataRequest(
          request = request.request,
          internalId = request.identifier,
          credentials = request.credentials,
          affinityGroup = request.affinityGroup,
          userAnswers = Some(userAnswers)))
    }
}
