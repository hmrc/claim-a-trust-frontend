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

package models

import play.api.libs.json.{Json, Writes}

final case class TaxEnrolmentsRequest(identifier: String)

object TaxEnrolmentsRequest {

  object Taxable {
    val IDENTIFIER = "SAUTR"
    val VERIFIER = "SAUTR1"
  }

  object NonTaxable {
    val IDENTIFIER = "URN"
    val VERIFIER = "URN1"
  }

  implicit val writes: Writes[TaxEnrolmentsRequest] = Writes { self => {

    case class Values(identifier: String, verifier: String)

    val identifierAndVerifierKey = if (IsUTR(self.identifier)) {
      Values(Taxable.IDENTIFIER, Taxable.VERIFIER)
    } else {
      Values(NonTaxable.IDENTIFIER, NonTaxable.VERIFIER)
    }

    Json.obj(
      "identifiers" -> Json.arr(
        Json.obj(
          "key" -> identifierAndVerifierKey.identifier,
          "value" -> self.identifier
        )),
      "verifiers" -> Json.arr(
        Json.obj(
          "key" -> identifierAndVerifierKey.verifier,
          "value" -> self.identifier
        )
      )
    )
  }

  }

}
