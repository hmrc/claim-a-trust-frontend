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

package config

import com.google.inject.{Inject, Singleton}
import controllers.routes
import play.api.Configuration
import play.api.i18n.{Lang, Messages}
import play.api.mvc.Call
import uk.gov.hmrc.hmrcfrontend.config.ContactFrontendConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class FrontendAppConfig @Inject() (val configuration: Configuration,
                                   contactFrontendConfig: ContactFrontendConfig,
                                   servicesConfig: ServicesConfig) {

  lazy val taxableEnrolmentServiceName: String = configuration.get[String]("microservice.services.tax-enrolments.taxable.serviceName")
  lazy val nonTaxableEnrolmentServiceName: String = configuration.get[String]("microservice.services.tax-enrolments.non-taxable.serviceName")

  val betaFeedbackUrl = s"${contactFrontendConfig.baseUrl.get}/contact/beta-feedback?service=${contactFrontendConfig.serviceId.get}"

  lazy val trustsRegistration: String = configuration.get[String]("urls.trustsRegistration")
  lazy val authUrl: String = configuration.get[Service]("auth").baseUrl
  lazy val loginUrl: String = configuration.get[String]("urls.login")
  lazy val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")
  lazy val logoutUrl: String = configuration.get[String]("urls.logout")

  lazy val appName: String = configuration.get[String]("appName")

  lazy val trustsContinueUrl: String = {
    configuration.get[String]("urls.maintainContinue")
  }

  lazy val languageTranslationEnabled: Boolean =
    configuration.get[Boolean]("microservice.services.features.welsh-translation")

  lazy val trustsStoreUrl: String = configuration.get[Service]("microservice.services.trusts-store").baseUrl + "/trusts-store"

  lazy val taxEnrolmentsUrl: String = configuration.get[Service]("microservice.services.tax-enrolments").baseUrl + "/tax-enrolments"

  lazy val relationshipEstablishmentUrl: String =
    configuration.get[Service]("microservice.services.relationship-establishment").baseUrl + "/relationship-establishment"

  lazy val relationshipName: String =
    configuration.get[String]("microservice.services.self.relationship-establishment.name")

  lazy val relationshipTaxableIdentifier: String =
    configuration.get[String]("microservice.services.self.relationship-establishment.taxable.identifier")

  lazy val relationshipNonTaxableIdentifier: String =
    configuration.get[String]("microservice.services.self.relationship-establishment.nonTaxable.identifier")

  lazy val relationshipTTL: Int =
    configuration.get[Int]("microservice.services.test.relationship-establishment-frontend.mongo.ttl")

  private def relationshipEstablishmentFrontendPath(identifier: String): String =
    s"${configuration.get[String]("microservice.services.relationship-establishment-frontend.path")}/$identifier"

  private def relationshipEstablishmentFrontendHost: String =
    configuration.get[String]("microservice.services.relationship-establishment-frontend.host")

  private def stubbedRelationshipEstablishmentFrontendPath(utr: String): String =
    s"${configuration.get[String]("microservice.services.test.relationship-establishment-frontend.path")}/$utr"

  private def stubbedRelationshipEstablishmentFrontendHost: String =
    configuration.get[String]("microservice.services.test.relationship-establishment-frontend.host")

  lazy val relationshipEstablishmentStubbed: Boolean =
    configuration.get[Boolean]("microservice.services.features.stubRelationshipEstablishment")

  def relationshipEstablishmentFrontendUrl(identifier: String): String = {
    if (relationshipEstablishmentStubbed) {
      s"${stubbedRelationshipEstablishmentFrontendHost}/${stubbedRelationshipEstablishmentFrontendPath(identifier)}"
    } else {
      s"${relationshipEstablishmentFrontendHost}/${relationshipEstablishmentFrontendPath(identifier)}"
    }
  }

  def relationshipEstablishmentBaseUrl: String = servicesConfig.baseUrl("test.relationship-establishment")

  lazy val successUrl: String =
    configuration.get[String]("microservice.services.self.relationship-establishment.successUrl")

  lazy val failureUrl: String =
    configuration.get[String]("microservice.services.self.relationship-establishment.failureUrl")

  lazy val countdownLength: Int = configuration.get[Int]("timeout.countdown")
  lazy val timeoutLength: Int = configuration.get[Int]("timeout.length")

  def helplineUrl(implicit messages: Messages): String = {
    val path = messages.lang.code match {
      case "cy" => "urls.welshHelpline"
      case _ => "urls.trustsHelpline"
    }
    configuration.get[String](path)
  }

  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  def routeToSwitchLanguage: String => Call =
    (lang: String) => routes.LanguageSwitchController.switchToLanguage(lang)

  lazy val cachettlInSeconds: Long = configuration.get[Long]("mongodb.timeToLiveInSeconds")

  lazy val dropIndexes: Boolean = configuration.getOptional[Boolean]("microservice.services.features.mongo.dropIndexes").getOrElse(false)

}
