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
import connectors.{RelationshipEstablishmentConnector, TrustsStoreConnector}
import errors.{TrustErrors, UpstreamRelationshipError}
import models.RelationshipEstablishmentStatus.RelationshipEstablishmentStatus
import models.auditing.Events.CLAIM_A_TRUST_FAILURE
import models.auditing.FailureReasons
import models.{RelationshipEstablishmentStatus, TrustsStoreRequest}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.mockito.MockitoSugar.mock
import org.scalatest.EitherValues
import pages.{IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.AuditService
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.Future

class IvFailureControllerSpec extends SpecBase with EitherValues {

  lazy val connector: RelationshipEstablishmentConnector = mock[RelationshipEstablishmentConnector]
  private val mockAuditService: AuditService = mock[AuditService]

  "IvFailure Controller" must {

    "callback-failure route" when {

      "redirect to IV FallbackFailure when no journeyId is provided" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(bind[AuditService].toInstance(mockAuditService))
          .build()

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"),
          eqTo(FailureReasons.IV_TECHNICAL_PROBLEM_NO_JOURNEY_ID))(any(), any())

        application.stop()
      }

      "redirect to trust locked page when user fails Trusts IV after multiple attempts" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService)
          )
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.Locked))))

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustLocked.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"), eqTo(FailureReasons.LOCKED))(any(), any())
        application.stop()
      }

      "redirect to trust utr not found page when the utr isn't found" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.NotFound))))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustNotFound.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"), eqTo(FailureReasons.IDENTIFIER_NOT_FOUND))(any(), any())

        application.stop()
      }

      "redirect to trust utr in processing page when the utr is processing" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.InProcessing))))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.IvFailureController.trustStillProcessing.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"), eqTo(FailureReasons.TRUST_STILL_PROCESSING))(any(), any())
        application.stop()
      }

      "redirect to trust utr Unsupported Relationship status page when the utr is processing" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.UnsupportedRelationshipStatus("")))))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"),
          eqTo(FailureReasons.UNSUPPORTED_RELATIONSHIP_STATUS))(any(), any())

        application.stop()
      }

      "redirect to trust utr Upstream Relationship error page when the utr is processing" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Left(UpstreamRelationshipError("")))))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"),
          eqTo(FailureReasons.UPSTREAM_RELATIONSHIP_ERROR))(any(), any())

        application.stop()
      }

      "redirect to IV FallbackFailure when no error key found in response" in {

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[RelationshipEstablishmentConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        when(connector.journeyId(any[String])(any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.NoRelationshipStatus))))

        val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

        val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url
        verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_FAILURE), eqTo("1234567890"),
          eqTo(FailureReasons.IV_TECHNICAL_PROBLEM_NO_ERROR_KEY))(any(), any())

        application.stop()
      }
    }

    "redirect to FallbackFailureController when unable to retrieve identifier" in {

      val answers = emptyUserAnswers
        .set(IsAgentManagingTrustPage, true).value

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(
          bind[RelationshipEstablishmentConnector].toInstance(connector),
          bind[AuditService].toInstance(mockAuditService))
        .build()

      when(connector.journeyId(any[String])(any(), any()))
        .thenReturn(EitherT[Future, TrustErrors, RelationshipEstablishmentStatus](Future.successful(Right(RelationshipEstablishmentStatus.NoRelationshipStatus))))

      val onIvFailureRoute = routes.IvFailureController.onTrustIvFailure.url

      val request = FakeRequest(GET, s"$onIvFailureRoute?journeyId=47a8a543-6961-4221-86e8-d22e2c3c91de")

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.FallbackFailureController.onPageLoad.url

      application.stop()
    }
  }

    "locked route" when {

      "return OK and the correct view for a GET for locked route" in {

        val onLockedRoute = routes.IvFailureController.trustLocked.url
        val utr = "3000000001"
        val managedByAgent = true
        val trustLocked = true

        val connector = mock[TrustsStoreConnector]

        when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

        val answers = emptyUserAnswers
          .set(IdentifierPage, utr).value
          .set(IsAgentManagingTrustPage, true).value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[TrustsStoreConnector].toInstance(connector),
            bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) must include("As you have had 3 unsuccessful tries at accessing this trust you will need to try again in 30 minutes.")

        verify(connector).claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any())

        application.stop()
      }

      "return Internal Server when trustLocked fails" in {

        val onLockedRoute = routes.IvFailureController.trustLocked.url
        val utr = "3000000001"
        val managedByAgent = true
        val trustLocked = true

        val connector = mock[TrustsStoreConnector]

        when(connector.claim(eqTo(TrustsStoreRequest(userAnswersId, utr, managedByAgent, trustLocked)))(any(), any(), any()))
          .thenReturn(EitherT[Future, TrustErrors, Boolean](Future.successful(Right(true))))

        val answers = emptyUserAnswers
          .set(IdentifierPage, utr).value
          .set(IsAgentManagingTrustPage, true).value

//        val application = applicationBuilder(userAnswers = Some(answers))
//          .overrides(
//            bind[TrustsStoreConnector].toInstance(connector),
//            bind[AuditService].toInstance(mockAuditService))
//          .build()

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual INTERNAL_SERVER_ERROR

        contentType(result) mustBe Some("text/html")

        application.stop()
      }

      "return session expired when GET for locked route" in {

        val onLockedRoute = routes.IvFailureController.trustLocked.url

        val answers = emptyUserAnswers

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

      "return OK and the correct view for a GET for not found route" in {

        val onLockedRoute = routes.IvFailureController.trustNotFound.url

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567890").value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) must include("The unique identifier you gave for the trust does not match our records")

        application.stop()
      }

      "return session expired when GET for not found route" in {

        val onLockedRoute = routes.IvFailureController.trustNotFound.url

        val answers = emptyUserAnswers

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

      "return OK and the correct view for a GET for still processing route" in {

        val onLockedRoute = routes.IvFailureController.trustStillProcessing.url

        val answers = emptyUserAnswers
          .set(IdentifierPage, "1234567891").value

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual OK

        application.stop()
      }

      "return session expired when GET for still processing route" in {

        val onLockedRoute = routes.IvFailureController.trustStillProcessing.url

        val answers = emptyUserAnswers

        val application = applicationBuilder(userAnswers = Some(answers))
          .overrides(
            bind[AuditService].toInstance(mockAuditService))
          .build()

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustBe routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }

      "redirect to Session Expired for a GET if no existing data is found" in {

        val application = applicationBuilder(userAnswers = None)
          .overrides(bind[AuditService].toInstance(mockAuditService))
          .build()

        val onLockedRoute = routes.IvFailureController.trustLocked.url

        val request = FakeRequest(GET, onLockedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

        application.stop()
      }
    }
}


