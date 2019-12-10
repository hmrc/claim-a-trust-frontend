/*
 * Copyright 2019 HM Revenue & Customs
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
import views.html.BeforeYouContinueView

class BeforeYouContinueViewSpec extends ViewBehaviours {

  "BeforeYouContinue view" must {

    "display specific content before 1st time going through IV" when {

      val view = viewFor[BeforeYouContinueView](Some(emptyUserAnswers))

      val applyView = view("0987654321", false)(fakeRequest, messages)

      behave like normalPage(applyView, "beforeYouContinue" , "p1", "p2", "p3", "p4")

      behave like pageWithBackLink(applyView)
    }

    "display specific content when returning user already enrolled" when {

      val view = viewFor[BeforeYouContinueView](Some(emptyUserAnswers))

      val applyView = view("0987654321", true)(fakeRequest, messages)

      behave like normalPage(applyView, "beforeYouContinue", "p5", "p2", "p3", "p4")

      behave like pageWithBackLink(applyView)
    }
  }
}
