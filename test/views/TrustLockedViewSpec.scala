/*
 * Copyright 2022 HM Revenue & Customs
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

package views

import views.behaviours.ViewBehaviours
import views.html.TrustLocked

class TrustLockedViewSpec extends ViewBehaviours {

  val utr = "0987654321"
  val urn = "ABTRUST12345678"

  "TrustLocked view" must {

    val view = viewFor[TrustLocked](Some(emptyUserAnswers))

    def applyView(id: String) = view.apply(id)(fakeRequest, messages)

    behave like normalPageWithCaption(applyView(utr), "locked", "utr", utr,"p1", "p2", "p3", "p4", "link1")

    "display the correct subheading for a utr" in {
      val doc = asDocument(applyView(utr))
      assertContainsText(doc, messages("utr.subheading", utr))
    }

    "display the correct subheading for a urn" in {
      val doc = asDocument(applyView(urn))
      assertContainsText(doc, messages("urn.subheading", urn))
    }

  }

}