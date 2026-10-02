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

package views.behaviours

import play.api.data.{Form, FormError}
import play.twirl.api.HtmlFormat
import views.ViewUtils

trait YesNoViewBehaviours extends ViewBehaviours {

  val errorKey     = "value"
  val errorMessage = "error.number"
  val error        = FormError(errorKey, errorMessage)

  val form: Form[Boolean]

  def yesNoPage(
    form: Form[Boolean],
    createView: Form[Boolean] => HtmlFormat.Appendable,
    messageKeyPrefix: String,
    expectedFormAction: String
  ): Unit =

    "behave like a page with a Yes/No question" when {

      "rendered" must {
        val doc = asDocument(createView(form))

        "contain a legend for the question" in {
          val legends = doc.getElementsByTag("legend")
          legends.size     mustBe 1
          legends.first.text must include(messages(s"$messageKeyPrefix.heading"))
        }

        "contain an input for the value" in {
          assertRenderedById(doc, "value-yes")
          assertRenderedById(doc, "value-no")
        }

        "have no values checked when rendered with no form" in {
          assert(!doc.getElementById("value-yes").hasAttr("checked"))
          assert(!doc.getElementById("value-no").hasAttr("checked"))
        }

        "not render an error summary" in
          assertNotRenderedById(doc, "error-summary_header")

        "have a form that submits to the correct action" in {
          val forms = doc.getElementsByTag("form")
          forms.size                             mustBe 1
          forms.first.attr("action")             mustBe expectedFormAction
          forms.first.attr("method").toLowerCase mustBe "post"
        }
      }

      "rendered with a value of true" must {

        behave like answeredYesNoPage(createView, true)
      }

      "rendered with a value of false" must {

        behave like answeredYesNoPage(createView, false)
      }

      "rendered with an error" must {

        val doc = asDocument(createView(form.withError(error)))

        "show an error summary" in
          assertRenderedByClass(doc, "govuk-error-summary")

        "show an error in the value field's label" in {
          val errorSpan = doc.getElementsByClass("govuk-error-message").first
          errorSpan.text mustBe s"""${messages("site.error")} ${messages(errorMessage)}"""
        }

        "show an error prefix in the browser title" in
          assertEqualsValue(
            doc,
            "title",
            ViewUtils.breadcrumbTitle(
              s"""${messages("error.browser.title.prefix")} ${messages(s"$messageKeyPrefix.title")}"""
            )
          )
      }
    }

  def answeredYesNoPage(createView: Form[Boolean] => HtmlFormat.Appendable, answer: Boolean): Unit = {
    val doc = asDocument(createView(form.fill(answer)))

    "have only the correct value checked" in {
      assert(doc.getElementById("value-yes").hasAttr("checked") == answer)
      assert(doc.getElementById("value-no").hasAttr("checked") != answer)
    }

    "not render an error summary" in
      assertNotRenderedById(doc, "error-summary_header")
  }

}
