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

package services

import base.SpecBase
import models.auditing.{ClaimATrustAuditFailureEvent, ClaimATrustAuditSuccessEvent}
import models.requests.IdentifierRequest
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContent
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.AffinityGroup._
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class AuditServiceSpec extends SpecBase with MockitoSugar {

  private val auditConnector: AuditConnector = mock[AuditConnector]
  private val auditService: AuditService = new AuditService(auditConnector)

  private val event: String = "event"
  private val identifier: String = "utr"
  private val ggCredId = "ggCredId"
  private val ggCredType = "GG"
  private val internalAuthId = "internalAuthId"
  private val enrolmentName = "enrolmentName"
  private val enrolmentId = "enrolmentId"

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  "Audit service" must {

    "build audit success payload from request values" when {

      "agent affinity; utr identifier" in {

        reset(auditConnector)

        val affinity: AffinityGroup = Agent


        val request: IdentifierRequest[AnyContent] = IdentifierRequest(fakeRequest, identifier, Credentials(ggCredId, ggCredType), affinity)

        auditService.audit(event, internalAuthId, enrolmentName, enrolmentId)(request, hc)

        val expectedPayload = ClaimATrustAuditSuccessEvent(
          credentialsId = ggCredId,
          credentialsType = ggCredType,
          internalAuthId = internalAuthId,
          identifier = identifier,
          enrolmentName = enrolmentName,
          enrolmentIdentifier = enrolmentId,
          isManagedByAgent = true
        )

        verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())
      }

      "org affinity; urn identifier" in {

        reset(auditConnector)

        val affinity: AffinityGroup = Organisation

        val request: IdentifierRequest[AnyContent] = IdentifierRequest(fakeRequest, identifier, Credentials(ggCredId, ggCredType), affinity)

        auditService.audit(event, internalAuthId, enrolmentName, enrolmentId)(request, hc)

        val expectedPayload = ClaimATrustAuditSuccessEvent(
          credentialsId = ggCredId,
          credentialsType = ggCredType,
          internalAuthId = internalAuthId,
          identifier = identifier,
          enrolmentName = enrolmentName,
          enrolmentIdentifier = enrolmentId,
          isManagedByAgent = false
        )

        verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())
      }
    }

    "build audit failure payload from request values" in {

      reset(auditConnector)

      val affinity: AffinityGroup = Agent

      val failureReason = "Error message"
      val request: IdentifierRequest[AnyContent] = IdentifierRequest(fakeRequest, identifier, Credentials(ggCredId, ggCredType), affinity)

      auditService.auditFailure(event, internalAuthId, failureReason)(request, hc)

      val expectedPayload = ClaimATrustAuditFailureEvent(
        credentialsId = ggCredId,
        credentialsType = ggCredType,
        internalAuthId = internalAuthId,
        identifier = identifier,
        failureReason = failureReason
      )

      verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())
    }
  }

}
