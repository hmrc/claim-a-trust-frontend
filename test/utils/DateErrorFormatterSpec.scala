/*
 * Copyright 2025 HM Revenue & Customs
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

package utils

import base.SpecBase
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.Helpers.stubMessagesApi

class DateErrorFormatterSpec extends SpecBase{

  "DateErrorFormatter.formatArgs" should {

    val messagesApi: MessagesApi = stubMessagesApi(
      Map("en" -> Map(
        "date.day" -> "Day",
        "date.month" -> "Month",
        "date.year" -> "Year"
      ))
    )
    implicit val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

    "Convert date args to lowercase strings" in {
      val inputArgs = Seq("day", "month", "year")
      val result = DateErrorFormatter.formatArgs(inputArgs)
      result mustBe Seq("day", "month", "year")
    }

    "Leave non-date args unchanged as strings" in {
      val inputArgs = Seq("test", 123, "tests")
      val result = DateErrorFormatter.formatArgs(inputArgs)
      result mustBe Seq("test", "123", "tests")
    }

    "Handle a mix of date and non-date args" in {
      val inputArgs = Seq("day", "test", "year", 42)
      val result = DateErrorFormatter.formatArgs(inputArgs)
      result mustBe Seq("day", "test", "year", "42")
    }
  }

}
