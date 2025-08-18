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

package base

import config.FrontendAppConfig
import controllers.actions._
import handlers.ErrorHandler
import models.UserAnswers
import org.scalatest.{EitherValues, TryValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{Injector, bind}
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.SessionRepository
import services.{FakeRelationshipEstablishmentService, RelationshipEstablishment}

import scala.concurrent.ExecutionContext.Implicits.global

trait SpecBase extends PlaySpec with GuiceOneAppPerSuite with TryValues with ScalaFutures with IntegrationPatience {

  val userAnswersId = "id"

  val fakeInternalId = "internalId"

  def emptyUserAnswers: UserAnswers = UserAnswers(userAnswersId, Json.obj())

  def injector: Injector = app.injector

  implicit def frontendAppConfig: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]

  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]

  implicit def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  implicit def messages: Messages = messagesApi.preferred(fakeRequest)

  val sessionRepository: SessionRepository = injector.instanceOf[SessionRepository]

  val errorHandler: ErrorHandler = injector.instanceOf[ErrorHandler]

  protected def applicationBuilder(
                                    userAnswers: Option[UserAnswers] = None,
                                    relationshipEstablishment: RelationshipEstablishment = new FakeRelationshipEstablishmentService()
                                  ): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        Seq(
          "play.filters.disabled" -> List("play.filters.csrf.CSRFFilter", "play.filters.csp.CSPFilter"),
          "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes"
        ) :_*
      )
      .overrides(
        bind[DataRequiredAction].to[DataRequiredActionImpl],
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[DataRetrievalRefinerAction].toInstance(new FakeDataRetrievalRefinerAction(userAnswers, sessionRepository, errorHandler)),
        bind[RelationshipEstablishment].toInstance(relationshipEstablishment)
      )
}
