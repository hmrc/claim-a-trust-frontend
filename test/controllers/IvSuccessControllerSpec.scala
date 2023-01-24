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
import connectors.TaxEnrolmentsConnector
import models.auditing.Events.{CLAIM_A_TRUST_ERROR, CLAIM_A_TRUST_SUCCESS}
import models.{EnrolmentCreated, TaxEnrolmentsRequest, UpstreamTaxEnrolmentsError, UserAnswers}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.mockito.MockitoSugar.mock
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SessionRepository
import services.{AuditService, RelationshipEstablishment, RelationshipFound}
import uk.gov.hmrc.http.BadRequestException
import views.html.IvSuccessView

import scala.concurrent.Future

class IvSuccessControllerSpec extends SpecBase with BeforeAndAfterEach {

  private val utr = "0987654321"
  private val urn = "ABTRUST12345678"

  private val connector = mock[TaxEnrolmentsConnector]
  private val mockRelationshipEstablishment = mock[RelationshipEstablishment]
  private val mockAuditService: AuditService = mock[AuditService]

  // Mock mongo repository
  private val mockRepository = mock[SessionRepository]

  override def beforeEach {
    reset(connector)
    reset(mockRelationshipEstablishment)
    reset(mockRepository)
    reset(mockAuditService)
    super.beforeEach()
  }

  "IvSuccess Controller" when {

    "claiming a trust" must {

      "return OK with the correct view for a GET with no Agent and set hasEnrolled true" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false).success.value
          .set(IdentifierPage, utr).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        // Stub a mongo connection
        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = false, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        // Verify if the HasEnrolled value is being set in mongo
        val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).success.value
        verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))

        verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())

        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())

        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(false))(any(), any())

        application.stop()

      }

      "return OK with the correct view for a GET with Agent and set hasEnrolled true" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true).success.value
          .set(IdentifierPage, utr).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = true, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).success.value
        verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))

        verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())

        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())

        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(true))(any(), any())

        application.stop()

      }

    }

    "claiming a trust again after a failure" must {

      "return OK with the correct view for a GET with no Agent and set hasEnrolled true" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false).success.value
          .set(IdentifierPage, utr).success.value
          .set(HasEnrolled, false).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        // Stub a mongo connection
        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = false, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        // Verify if the HasEnrolled value is being set in mongo
        val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).success.value
        verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))

        verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())

        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())

        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(false))(any(), any())

        application.stop()

      }

      "return OK with the correct view for a GET with Agent and set hasEnrolled true" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true).success.value
          .set(IdentifierPage, utr).success.value
          .set(HasEnrolled, false).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = true, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        val userAnswersWithHasEnrolled = userAnswers.set(HasEnrolled, true).success.value
        verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolled))

        verify(connector, atLeastOnce()).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())

        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())

        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(utr), eqTo(true))(any(), any())

        application.stop()

      }

    }

    "rendering page after having claimed" must {

      "return OK and the correct view for a GET with no Agent and has enrolled" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false).success.value
          .set(IdentifierPage, utr).success.value
          .set(HasEnrolled, true).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        )
          .overrides(
            bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
            bind(classOf[SessionRepository]).toInstance(mockRepository),
            bind(classOf[AuditService]).toInstance(mockAuditService)
          ).build()

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = false, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        verify(mockRepository, never()).set(any())

        verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(any(), any(), any())

        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())
        verify(mockAuditService, never()).audit(any(), any(), any())(any(), any())

        application.stop()

      }

      "return OK and the correct view for a GET with Agent and has enrolled" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true).success.value
          .set(IdentifierPage, utr).success.value
          .set(HasEnrolled, true).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = true, utr)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        verify(mockRepository, never()).set(any())

        verify(connector, never()).enrol(any[TaxEnrolmentsRequest]())(any(), any(), any())
        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())
        verify(mockAuditService, never()).audit(any(), any(), any())(any(), any())

        application.stop()

      }

    }

    "claiming a URN" must {

      "return OK and the correct view for a GET with no Agent" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, false).success.value
          .set(IdentifierPage, urn).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = false, urn)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(urn))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(urn)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        verify(connector).enrol(eqTo(TaxEnrolmentsRequest(urn)))(any(), any(), any())
        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(urn))(any())
        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(urn), eqTo(false))(any(), any())

        application.stop()

      }

      "return OK and the correct view for a GET with Agent" in {

        val userAnswers = UserAnswers(userAnswersId)
          .set(IsAgentManagingTrustPage, true).success.value
          .set(IdentifierPage, urn).success.value

        val application = applicationBuilder(
          userAnswers = Some(userAnswers),
          relationshipEstablishment = mockRelationshipEstablishment
        ).overrides(
          bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
          bind(classOf[SessionRepository]).toInstance(mockRepository),
          bind(classOf[AuditService]).toInstance(mockAuditService)
        ).build()

        when(mockRepository.set(any())).thenReturn(Future.successful(true))

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val view = application.injector.instanceOf[IvSuccessView]

        val viewAsString = view(isAgent = true, urn)(request, messages).toString

        when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(urn))(any()))
          .thenReturn(Future.successful(RelationshipFound))

        when(connector.enrol(eqTo(TaxEnrolmentsRequest(urn)))(any(), any(), any()))
          .thenReturn(Future.successful(EnrolmentCreated))

        val result = route(application, request).value

        status(result) mustEqual OK

        contentAsString(result) mustEqual viewAsString

        verify(connector).enrol(eqTo(TaxEnrolmentsRequest(urn)))(any(), any(), any())
        verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(urn))(any())
        verify(mockAuditService).audit(eqTo(CLAIM_A_TRUST_SUCCESS), eqTo(urn), eqTo(true))(any(), any())

        application.stop()

      }

    }

    "redirect to maintain" when {

      "user continues and checks status of the trust" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        val request = FakeRequest(POST, routes.IvSuccessController.onSubmit.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual "http://localhost:9788/maintain-a-trust/status"

        application.stop()
      }
    }

    "redirect to Session Expired" when {

      "no existing data is found" in {

        val application = applicationBuilder(userAnswers = None).build()

        val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url

        application.stop()

      }

      "redirect to Internal Server Error" when {

        "tax enrolments fails" when {

          "401 UNAUTHORIZED" in {

            val utr = "1234567890"

            val userAnswers = UserAnswers(userAnswersId)
              .set(IsAgentManagingTrustPage, true).success.value
              .set(IdentifierPage, utr).success.value

            val application = applicationBuilder(
              userAnswers = Some(userAnswers),
              relationshipEstablishment = mockRelationshipEstablishment
            )
              .overrides(
                bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
                bind(classOf[SessionRepository]).toInstance(mockRepository),
                bind(classOf[AuditService]).toInstance(mockAuditService)
              ).build()

            val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

            // Stub a mongo connection
            when(mockRepository.set(any())).thenReturn(Future.successful(true))

            when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
              .thenReturn(Future.successful(RelationshipFound))

            when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
              .thenReturn(Future.failed(UpstreamTaxEnrolmentsError("Unauthorized")))

            val result = route(application, request).value

            status(result) mustEqual INTERNAL_SERVER_ERROR

            // Verify if the HasEnrolled value is being unset in mongo in case of errors
            val userAnswersWithHasEnrolledUnset = userAnswers.set(HasEnrolled, false).success.value
            verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolledUnset))

            verify(connector).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())
            verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())
            verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_ERROR), eqTo(utr), eqTo("Unauthorized"))(any(), any())

            application.stop()

          }

          "400 BAD_REQUEST" in {

            val utr = "0987654321"

            val userAnswers = UserAnswers(userAnswersId)
              .set(IsAgentManagingTrustPage, true).success.value
              .set(IdentifierPage, utr).success.value

            val application = applicationBuilder(
              userAnswers = Some(userAnswers),
              relationshipEstablishment = mockRelationshipEstablishment
            )
              .overrides(
                bind(classOf[TaxEnrolmentsConnector]).toInstance(connector),
                bind(classOf[SessionRepository]).toInstance(mockRepository),
                bind(classOf[AuditService]).toInstance(mockAuditService)
              ).build()

            val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad.url)

            // Stub a mongo connection
            when(mockRepository.set(any())).thenReturn(Future.successful(true))

            when(mockRelationshipEstablishment.check(eqTo("id"), eqTo(utr))(any()))
              .thenReturn(Future.successful(RelationshipFound))

            when(connector.enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any()))
              .thenReturn(Future.failed(new BadRequestException("BadRequest")))

            val result = route(application, request).value

            status(result) mustEqual INTERNAL_SERVER_ERROR

            // Verify if the HasEnrolled value is being unset in mongo in case of errors
            val userAnswersWithHasEnrolledUnset = userAnswers.set(HasEnrolled, false).success.value
            verify(mockRepository, times(1)).set(eqTo(userAnswersWithHasEnrolledUnset))

            verify(connector).enrol(eqTo(TaxEnrolmentsRequest(utr)))(any(), any(), any())
            verify(mockRelationshipEstablishment).check(eqTo("id"), eqTo(utr))(any())
            verify(mockAuditService).auditFailure(eqTo(CLAIM_A_TRUST_ERROR), eqTo(utr), eqTo("BadRequest"))(any(), any())

            application.stop()

          }
        }

      }

    }

  }
}