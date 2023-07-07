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

import base.SpecBase
import config.FrontendAppConfig
import models.UserAnswers
import models.auditing.{ClaimATrustAuditFailureEvent, ClaimATrustAuditSuccessEvent}
import models.requests.DataRequest
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import play.api.mvc.AnyContent
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.AffinityGroup._
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import scala.concurrent.ExecutionContext

class AuditServiceSpec (implicit ec: ExecutionContext) extends SpecBase with MockitoSugar {

  private val auditConnector: AuditConnector = mock[AuditConnector]
  lazy val config: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  private val auditService: AuditService = new AuditService(auditConnector, config, ec)

  private val event: String = "event"
  private val utr: String = "1234567890"
  private val urn: String = "NTTRUST0000001"
  private val ggCredId = "ggCredId"
  private val ggCredType = "GG"
  private val internalAuthId = "internalAuthId"

  lazy val taxableEnrolmentServiceName: String = s"HMRC-TERS-ORG"
  lazy val nonTaxableEnrolmentServiceName: String = "HMRC-TERSNT-ORG"

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  "Audit service" must {

    "build audit success payload from request values for Taxable" when {

      reset(auditConnector)

      val affinity: AffinityGroup = Agent

      val request: DataRequest[AnyContent] = DataRequest(fakeRequest, internalAuthId, Credentials(ggCredId, ggCredType), affinity, UserAnswers(""))

      auditService.audit(event, utr, true)(request, hc)

      val expectedPayload = ClaimATrustAuditSuccessEvent(
        credentialsId = ggCredId,
        credentialsType = ggCredType,
        internalAuthId = internalAuthId,
        enrolmentName = taxableEnrolmentServiceName,
        enrolmentIdentifier = utr,
        isManagedByAgent = true
      )

      verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())
    }

    "build audit success payload from request values for NonTaxable" when {

      reset(auditConnector)

      val affinity: AffinityGroup = Agent

      val request: DataRequest[AnyContent] = DataRequest(fakeRequest, internalAuthId, Credentials(ggCredId, ggCredType), affinity, UserAnswers(""))

      auditService.audit(event, urn, true)(request, hc)

      val expectedPayload = ClaimATrustAuditSuccessEvent(
        credentialsId = ggCredId,
        credentialsType = ggCredType,
        internalAuthId = internalAuthId,
        enrolmentName = nonTaxableEnrolmentServiceName,
        enrolmentIdentifier = urn,
        isManagedByAgent = true
      )

      verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())

    }

    "build audit failure payload from request values" in {

      reset(auditConnector)

      val affinity: AffinityGroup = Agent

      val failureReason = "Error message"
      val request: DataRequest[AnyContent] = DataRequest(fakeRequest, internalAuthId, Credentials(ggCredId, ggCredType), affinity, UserAnswers(""))

      auditService.auditFailure(event, utr, failureReason)(request, hc)

      val expectedPayload = ClaimATrustAuditFailureEvent(
        credentialsId = ggCredId,
        credentialsType = ggCredType,
        internalAuthId = internalAuthId,
        identifier = utr,
        failureReason = failureReason
      )

      verify(auditConnector).sendExplicitAudit(eqTo(event), eqTo(expectedPayload))(any(), any(), any())
    }
  }

}
