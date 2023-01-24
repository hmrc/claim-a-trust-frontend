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

package views

import views.behaviours.ViewBehaviours
import views.html.IvSuccessView

class IvSuccessViewSpec extends ViewBehaviours {

  val utr = "1234567890"
  val urn = "ABTRUST12345678"

  "IvSuccess view with Agent" must {

    "display the register link" when {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .build()

      val view = application.injector.instanceOf[IvSuccessView]

      val applyView = view.apply(isAgent = true, utr)(fakeRequest, messages)

      behave like normalPageWithCaption(applyView, "ivSuccess.agent", "utr", utr,"paragraph1", "paragraph2", "paragraph3",
        "paragraph4", "paragraph5", "paragraph6")
    }

    "display the correct subheading for a utr" in {

      val view = viewFor[IvSuccessView](Some(emptyUserAnswers))

      val applyView = view.apply(isAgent = true, utr)(fakeRequest, messages)

      val doc = asDocument(applyView)
      assertContainsText(doc, messages("utr.subheading", utr))
    }

    "display the correct subheading for a urn" in {

      val view = viewFor[IvSuccessView](Some(emptyUserAnswers))

      val applyView = view.apply(isAgent = true, urn)(fakeRequest, messages)

      val doc = asDocument(applyView)
      assertContainsText(doc, messages("urn.subheading", urn))
    }
  }

  "IvSuccess view with no Agent" must {

    "render view" when {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .build()

      val view = application.injector.instanceOf[IvSuccessView]

      def applyView(id: String) = view.apply(isAgent = false, id)(fakeRequest, messages)

      behave like normalPageWithCaption(applyView(utr), "ivSuccess.no.agent","utr", utr, "paragraph1", "li.1", "li.2", "li.3", "paragraph2","paragraph3")

      "display the correct subheading for a utr" in {
        val doc = asDocument(applyView(utr))
        assertContainsText(doc, messages("utr.subheading", utr))
      }

      "display the correct subheading for a urn" in {
        val doc = asDocument(applyView(urn))
        assertContainsText(doc, messages("urn.subheading", urn))
      }

      "show the continue button" in {
        val doc = asDocument(applyView(utr))
        assertRenderedByCssSelector(doc, ".button")
      }

    }


  }

}
