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

import base.SpecBase
import cats.data.EitherT
import errors.TrustErrors
import handlers.ErrorHandler
import models.UserAnswers
import models.requests.{IdentifierRequest, OptionalDataRequest}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import repositories.SessionRepository
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core.retrieve.Credentials

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataRetrievalRefinerActionSpec extends SpecBase with MockitoSugar with ScalaFutures with EitherValues {

  private val mockErrorHandler = mock[ErrorHandler]

  class Harness(sessionRepository: SessionRepository)
      extends DataRetrievalRefinerAction(sessionRepository, mockErrorHandler) {
    def callRefine[A](request: IdentifierRequest[A]): Future[Either[Result, OptionalDataRequest[A]]] = refine(request)
  }

  "Data Retrieval Action" when {

    "there is no data in the cache" must {

      "set userAnswers to 'None' in the request" in {

        val sessionRepository = mock[SessionRepository]
        when(sessionRepository.get("id"))
          .thenReturn(EitherT[Future, TrustErrors, Option[UserAnswers]](Future.successful(Right(None))))
        val action            = new Harness(sessionRepository)

        val futureResult =
          action.callRefine(new IdentifierRequest(fakeRequest, "id", Credentials("providerId", "GG"), Organisation))

        whenReady(futureResult) { result =>
          result.value.userAnswers.isEmpty mustBe true
        }
      }
    }

    "there is data in the cache" must {

      "build a userAnswers object and add it to the request" in {

        val sessionRepository = mock[SessionRepository]
        when(sessionRepository.get("id"))
          .thenReturn(
            EitherT[Future, TrustErrors, Option[UserAnswers]](Future.successful(Right(Some(new UserAnswers("id")))))
          )
        val action            = new Harness(sessionRepository)

        val futureResult =
          action.callRefine(new IdentifierRequest(fakeRequest, "id", Credentials("providerId", "GG"), Organisation))

        whenReady(futureResult) { result =>
          result.value.userAnswers.isDefined mustBe true
        }
      }
    }
  }

}
