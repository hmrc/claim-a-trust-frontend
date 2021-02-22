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

package models

import com.google.inject.Inject
import config.FrontendAppConfig
import uk.gov.hmrc.auth.core.{BusinessKey, Relationship}

class RelationshipForIdentifier @Inject()(config: FrontendAppConfig) {

  def apply(identifier: String): Relationship = {
    val businessKey = if (IsUTR(identifier)) {
      config.relationshipTaxableIdentifier
    } else {
      config.relationshipNonTaxableIdentifier
    }

    Relationship(config.relationshipName, Set(BusinessKey(businessKey, identifier)))
  }

}
