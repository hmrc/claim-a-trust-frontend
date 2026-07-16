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

package config

import base.SpecBase
import play.api.i18n.Lang
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

class FrontendAppConfigSpec extends SpecBase {

  private val appConfig = app.injector.instanceOf[FrontendAppConfig]

  "FrontendAppConfig" must {

    "have the correct languageMap" in {
      appConfig.languageMap mustBe Map(
        "english" -> Lang("en"),
        "cymraeg" -> Lang("cy")
      )
    }
    "return the correct route to switch language - CY" in {
      val cyCall = appConfig.routeToSwitchLanguage("cy")
      cyCall.url mustBe "/claim-a-trust/language/cy"
    }
    "return the correct route to switch language - EN" in {
      val enCall = appConfig.routeToSwitchLanguage("en")
      enCall.url mustBe "/claim-a-trust/language/en"
    }
    "return the correct loginUrl" in {
      appConfig.loginUrl mustBe "http://localhost:9949/auth-login-stub/gg-sign-in"
    }
    "return correct loginContinueUrl" in {
      appConfig.loginContinueUrl mustBe "http://localhost:9785/claim-a-trust"
    }
    "return the real relationship establishment URL when stubbing is disabled" in {

      val testApp = new GuiceApplicationBuilder()
        .configure(
          "microservice.services.features.stubRelationshipEstablishment"   -> false,
          "microservice.services.relationship-establishment-frontend.host" -> "http://real-host",
          "microservice.services.relationship-establishment-frontend.path" -> "relationship-establishment"
        )
        .build()

      running(testApp) {
        val config = testApp.injector.instanceOf[FrontendAppConfig]

        config.relationshipEstablishmentFrontendUrl("1234567890") mustBe
          "http://real-host/relationship-establishment/1234567890"
      }
    }

    "return the stubbed relationship establishment URL when stubbing is enabled" in {

      val testApp = new GuiceApplicationBuilder()
        .configure(
          "microservice.services.features.stubRelationshipEstablishment"        -> true,
          "microservice.services.test.relationship-establishment-frontend.host" -> "http://stub-host",
          "microservice.services.test.relationship-establishment-frontend.path" -> "stub-relationship-establishment"
        )
        .build()

      running(testApp) {
        val config = testApp.injector.instanceOf[FrontendAppConfig]

        config.relationshipEstablishmentFrontendUrl("1234567890") mustBe
          "http://stub-host/stub-relationship-establishment/1234567890"
      }
    }

  }

}
