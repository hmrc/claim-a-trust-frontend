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

package models.auditing

object FailureReasons {

  val LOCKED = "Failed 3 times, cred locked for 30 minutes"
  val SERVICE_UNAVAILABLE = "Service Unavailable (Trust IV)"
  val IDENTIFIER_NOT_FOUND = "Identifier not found"
  val TRUST_STILL_PROCESSING = "Trust is still processing"
  val IV_TECHNICAL_PROBLEM = "IV technical problem"
  val UNSUPPORTED_RELATIONSHIP_STATUS = "Unsupported relationship status"
  val UPSTREAM_RELATIONSHIP_ERROR = "Upstream relationship error"
}
