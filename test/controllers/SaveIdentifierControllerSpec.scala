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

package controllers

import base.SpecBase
import cats.data.EitherT
import errors.{InvalidIdentifier, NoData, ServerError, TrustErrors}
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.IdentifierPage
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SessionRepository
import services.{FakeRelationshipEstablishmentService, RelationEstablishmentStatus, RelationshipEstablishment, RelationshipNotFound}

import scala.concurrent.Future

class SaveIdentifierControllerSpec extends SpecBase with EitherValues {

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val fakeEstablishmentServiceFailing = new FakeRelationshipEstablishmentService(Right(RelationshipNotFound))
  val fakeEstablishmentServiceError = new FakeRelationshipEstablishmentService(Left(ServerError()))

  "SaveIdentifierController" when {

    "invalid identifier provided" must {

      "render an error page" in {

        val mockSessionRepository = mock[SessionRepository]

        val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

        val request = FakeRequest(GET, routes.SaveIdentifierController.save("123").url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe routes.FallbackFailureController.onPageLoad.url
      }

    }

    "could not save identifier" must {

      "render an error page" in {

        val mockSessionRepository = mock[SessionRepository]
        val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
        val mockRelationshipEstablishment = mock[RelationshipEstablishment]

        when(mockSessionRepository.set(captor.capture()))
                  .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(NoData))))

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(urn))(any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationEstablishmentStatus](Future.successful(Left(InvalidIdentifier))))

        val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

        val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

        val result = route(application, request).value

        status(result) mustEqual INTERNAL_SERVER_ERROR

        contentType(result) mustBe Some("text/html")

        application.stop()
      }
    }

    "utr provided" must {

      "save UTR to session repo" when {

        "user answers does not exist" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(utr).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

          captor.getValue.get(IdentifierPage).value mustBe utr

        }

        "user answers exists" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(utr).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

          captor.getValue.get(IdentifierPage).value mustBe utr

        }
      }

    }

    "urn provided" must {

      "save URN to session repo" when {

        "user answers does not exist" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

          captor.getValue.get(IdentifierPage).value mustBe urn

        }

        "user answers exists" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustBe routes.IsAgentManagingTrustController.onPageLoad(NormalMode).url

          captor.getValue.get(IdentifierPage).value mustBe urn

        }

        "return an internal server error when user answers do not exist" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

          val application = applicationBuilder(userAnswers = None, fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          contentType(result) mustBe Some("text/html")

          captor.getValue.get(IdentifierPage).value mustBe urn

        }

        "error while storing user answers" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val answers = emptyUserAnswers
            .set(IdentifierPage, "0987654321").value

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Left(ServerError()))))

          val application = applicationBuilder(userAnswers = Some(answers), fakeEstablishmentServiceFailing)
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual INTERNAL_SERVER_ERROR

          contentType(result) mustBe Some("text/html")

          application.stop()
        }

        "user directed to trust claimed" in {

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvSuccessController.onPageLoad.url
        }

        "user failed to claim trust" in {

          val mockSessionRepository = mock[SessionRepository]

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers), fakeEstablishmentServiceError)
            .overrides(
              bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(utr).url)

          val result = route(application, request).value

          status(result) mustEqual INTERNAL_SERVER_ERROR

          contentType(result) mustBe Some("text/html")

          application.stop()
        }
      }
    }
  }
}
