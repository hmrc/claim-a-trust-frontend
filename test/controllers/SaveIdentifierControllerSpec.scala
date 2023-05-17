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
import connectors.TaxEnrolmentsConnector
import errors.TrustErrors
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.EitherValues
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SessionRepository
import services.{AuditService, FakeRelationshipEstablishmentService, RelationshipNotFound}

import scala.concurrent.Future

class SaveIdentifierControllerSpec extends SpecBase with EitherValues {

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  val fakeEstablishmentServiceFailing = new FakeRelationshipEstablishmentService(RelationshipNotFound)

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

        "user directed to trust claimed" in {

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])

          val mockSessionRepository = mock[SessionRepository]

          when(mockSessionRepository.set(captor.capture()))
            .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
            .build()

          val request = FakeRequest(GET, routes.SaveIdentifierController.save(urn).url)

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.IvSuccessController.onPageLoad.url

          captor.getValue.get(IdentifierPage).value mustBe urn

        }
      }
    }
  }
}
