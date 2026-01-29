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

import base.SpecBase
import play.api.libs.json.Json

class TaxEnrolmentRequestSpec extends SpecBase {

  ".writes" must {

    "construct json for claiming a UTR" in {
      val utr     = "1234567890"
      val request = TaxEnrolmentsRequest(utr)

      Json.toJson(request) mustBe Json.parse("""
          |{
          | "identifiers": [
          |   {
          |     "key": "SAUTR",
          |     "value": "1234567890"
          |   }
          | ],
          | "verifiers": [
          |   {
          |     "key": "SAUTR1",
          |     "value": "1234567890"
          |   }
          | ]
          |}
          |""".stripMargin)

    }

    "construct json for claiming a URN" in {
      val urn     = "ABTRUST12345678"
      val request = TaxEnrolmentsRequest(urn)

      Json.toJson(request) mustBe Json.parse("""
          |{
          | "identifiers": [
          |   {
          |     "key": "URN",
          |     "value": "ABTRUST12345678"
          |   }
          | ],
          | "verifiers": [
          |   {
          |     "key": "URN1",
          |     "value": "ABTRUST12345678"
          |   }
          | ]
          |}
          |""".stripMargin)
    }

  }

}
