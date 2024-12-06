/*
 * Copyright 2024 HM Revenue & Customs
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

import config.FrontendAppConfig
import models.auditing._
import models.requests.DataRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import javax.inject.Inject
import models.IsUTR

import scala.concurrent.ExecutionContext

class AuditService @Inject()(auditConnector: AuditConnector,  config : FrontendAppConfig, implicit val ec: ExecutionContext) {

  def audit(event: String, identifier: String, isManagedByAgent: Boolean)
           (implicit request: DataRequest[_], hc: HeaderCarrier): Unit = {

    val enrolmentName: String = if (IsUTR(identifier)) {
      config.taxableEnrolmentServiceName
    } else {
      config.nonTaxableEnrolmentServiceName
    }
    val payload = ClaimATrustAuditSuccessEvent(
      credentialsId = request.credentials.providerId,
      credentialsType = request.credentials.providerType,
      internalAuthId = request.internalId,
      enrolmentName = enrolmentName,
      enrolmentIdentifier = identifier,
      isManagedByAgent = isManagedByAgent
    )

    auditConnector.sendExplicitAudit(event, payload)
  }

  def auditFailure(event: String, identifier: String, failureReason: String)
           (implicit request: DataRequest[_], hc: HeaderCarrier): Unit = {

    val payload = ClaimATrustAuditFailureEvent(
      credentialsId = request.credentials.providerId,
      credentialsType = request.credentials.providerType,
      internalAuthId = request.internalId,
      identifier = identifier,
      failureReason = failureReason
    )

    auditConnector.sendExplicitAudit(event, payload)
  }

}
