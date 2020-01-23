/*
 * Copyright 2020 HM Revenue & Customs
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
import views.html.TrustNotFound

class TrustNotFoundViewSpec extends ViewBehaviours {

  val utr = "0987654321"

  "TrustNotFound view" must {

    val view = viewFor[TrustNotFound](Some(emptyUserAnswers))

    val applyView = view.apply(utr)(fakeRequest, messages)

    behave like normalPage(applyView, "notFound","p1", "p2","p3",
      "p4", "link1", "p5", "link2", "link3")

    "display the correct heading" in {
      val doc = asDocument(applyView)
      assertContainsText(doc, messages("notFound.heading", utr))
    }

  }

}