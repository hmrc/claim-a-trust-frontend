/*
 * Copyright 2026 HM Revenue & Customs
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

package errors

import play.api.mvc.Result

sealed trait TrustErrors

final case class ServerError(exceptionMessage: String = "") extends TrustErrors

final case class TrustFormError(result: Result) extends TrustErrors

case object InvalidIdentifier extends TrustErrors

case object NoData extends TrustErrors

case class UpstreamRelationshipError(reason: String) extends TrustErrors

final case class UpstreamTaxEnrolmentsError(message: String) extends TrustErrors
