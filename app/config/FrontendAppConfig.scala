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

package config

import com.google.inject.{Inject, Singleton}
import controllers.routes
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class FrontendAppConfig @Inject() (configuration: Configuration, servicesConfig: ServicesConfig) {

  private val contactHost = configuration.get[String]("contact-frontend.host")
  private val contactFormServiceIdentifier = "claim-a-trust-frontend"

  lazy val serviceName: String = configuration.get[String]("serviceName")

  val analyticsToken: String = configuration.get[String](s"google-analytics.token")
  val analyticsHost: String = configuration.get[String](s"google-analytics.host")
  val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  val betaFeedbackUrl = s"$contactHost/contact/beta-feedback"
  val betaFeedbackUnauthenticatedUrl = s"$contactHost/contact/beta-feedback-unauthenticated"

  lazy val authUrl: String = configuration.get[Service]("auth").baseUrl
  lazy val loginUrl: String = configuration.get[String]("urls.login")
  lazy val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")

  lazy val useMaintainFrontend : Boolean =
    configuration.get[Boolean]("microservice.services.features.useMaintainFrontend.enabled")

  lazy val trustsContinueUrl: String = {
    if(useMaintainFrontend) {
      configuration.get[String]("urls.maintainContinue")
    } else {
      configuration.get[String]("urls.trustsContinue")
    }
  }

  lazy val languageTranslationEnabled: Boolean =
    configuration.get[Boolean]("microservice.services.features.welsh-translation")

  lazy val playbackEnabled: Boolean = configuration.get[Boolean]("microservice.services.features.playback.enabled")

  lazy val trustsStoreUrl: String = configuration.get[Service]("microservice.services.trusts-store").baseUrl + "/trusts-store"

  lazy val taxEnrolmentsUrl: String = configuration.get[Service]("microservice.services.tax-enrolments").baseUrl + "/tax-enrolments"

  lazy val relationshipEstablishmentUrl : String =
    configuration.get[Service]("microservice.services.relationship-establishment").baseUrl + "/relationship-establishment"

  lazy val relationshipName : String =
    configuration.get[String]("microservice.services.self.relationship-establishment.name")

  lazy val relationshipIdentifier : String =
    configuration.get[String]("microservice.services.self.relationship-establishment.identifier")

  private def relationshipEstablishmentFrontendPath(utr: String) : String =
    s"${configuration.get[String]("microservice.services.relationship-establishment-frontend.path")}/$utr"

  private def relationshipEstablishmentFrontendHost : String =
    configuration.get[String]("microservice.services.relationship-establishment-frontend.host")

  private def stubbedRelationshipEstablishmentFrontendPath(utr: String) : String =
    s"${configuration.get[String]("microservice.services.test.relationship-establishment-frontend.path")}/$utr"

  private def stubbedRelationshipEstablishmentFrontendHost : String =
    configuration.get[String]("microservice.services.test.relationship-establishment-frontend.host")

  lazy val relationshipEstablishmentStubbed: Boolean =
    configuration.get[Boolean]("microservice.services.features.stubRelationshipEstablishment")

  def relationshipEstablishmentFrontendtUrl(utr: String) : String = {
    if(relationshipEstablishmentStubbed) {
      s"${stubbedRelationshipEstablishmentFrontendHost}/${stubbedRelationshipEstablishmentFrontendPath(utr)}"
    } else {
      s"${relationshipEstablishmentFrontendHost}/${relationshipEstablishmentFrontendPath(utr)}"
    }
  }

  def relationshipEstablishmentBaseUrl : String = servicesConfig.baseUrl("test.relationship-establishment")

  lazy val successUrl : String =
    configuration.get[String]("microservice.services.self.relationship-establishment.successUrl")

  lazy val failureUrl : String =
    configuration.get[String]("microservice.services.self.relationship-establishment.failureUrl")

  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  def routeToSwitchLanguage: String => Call =
    (lang: String) => routes.LanguageSwitchController.switchToLanguage(lang)
}
