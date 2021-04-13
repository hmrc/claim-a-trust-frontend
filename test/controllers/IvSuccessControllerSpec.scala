/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{TaxEnrolmentsRequest, UpstreamTaxEnrolmentsError, UserAnswers}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.{HasEnrolled, IdentifierPage, IsAgentManagingTrustPage}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SessionRepository
import services.{RelationshipEstablishment, RelationshipFound}
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.Future

class IvSuccessControllerSpec extends SpecBase with BeforeAndAfterEach {

  private val connector = mock[TaxEnrolmentsConnector]

  private val mockRelationshipEstablishment = mock[RelationshipEstablishment]

  // Mock mongo repository
  private val mockRepository = mock[SessionRepository]

  override def beforeEach {
    reset(connector)
    reset(mockRelationshipEstablishment)
    reset(mockRepository)
    super.beforeEach()
  }

  "IvSuccess Controller" when {

    "redirect to Session Expired" when {

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
              bind(classOf[SessionRepository]).toInstance(mockRepository)
            ).build()

            val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad().url)

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
              bind(classOf[SessionRepository]).toInstance(mockRepository)
            ).build()

            val request = FakeRequest(GET, routes.IvSuccessController.onPageLoad().url)

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

            application.stop()

          }
        }

      }

    }

  }
}
