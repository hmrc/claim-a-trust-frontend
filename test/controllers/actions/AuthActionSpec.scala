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

package controllers.actions

import base.SpecBase
import controllers.routes
import models.requests.IdentifierRequest
import play.api.mvc.Results.Redirect
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.UnauthorizedException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthActionSpec extends SpecBase {

  class Harness(authAction: IdentifierAction) {

    def onPageLoad: Action[AnyContent] =
      authAction((request: IdentifierRequest[AnyContent]) =>
        Results.Ok(s"${request.identifier}|${request.credentials.providerId}|${request.affinityGroup}")
      )

  }

  private lazy val bodyParsers: BodyParsers.Default = app.injector.instanceOf[BodyParsers.Default]

  private def failingAuthAction(exception: Throwable): Harness =
    new Harness(new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(exception), bodyParsers))

  private def authActionReturning(
    internalId: Option[String],
    credentials: Option[Credentials],
    affinityGroup: Option[AffinityGroup]
  ): Harness = {
    val retrievals = new ~(new ~(internalId, credentials), affinityGroup)
    new Harness(new AuthenticatedIdentifierAction(new FakeAuthConnector(Future.successful(retrievals)), bodyParsers))
  }

  private val credentials = Credentials("providerId", "GovernmentGateway")

  "Auth Action" when {

    "the user is logged in with all retrievals" must {
      "call the block with an IdentifierRequest built from the retrievals" in {
        val controller = authActionReturning(Some("internalId"), Some(credentials), Some(AffinityGroup.Organisation))

        val result = controller.onPageLoad(fakeRequest)

        status(result)          mustBe OK
        contentAsString(result) mustBe "internalId|providerId|Organisation"
      }
    }

    "a retrieval is missing" must {

      "fail with an UnauthorizedException" when {

        "there is no internal ID" in {
          val controller = authActionReturning(None, Some(credentials), Some(AffinityGroup.Organisation))

          val exception = controller.onPageLoad(fakeRequest).failed.futureValue

          exception            mustBe an[UnauthorizedException]
          exception.getMessage mustBe "Unable to retrieve internal Id"
        }

        "there are no credentials" in {
          val controller = authActionReturning(Some("internalId"), None, Some(AffinityGroup.Organisation))

          controller.onPageLoad(fakeRequest).failed.futureValue mustBe an[UnauthorizedException]
        }

        "there is no affinity group" in {
          val controller = authActionReturning(Some("internalId"), Some(credentials), None)

          controller.onPageLoad(fakeRequest).failed.futureValue mustBe an[UnauthorizedException]
        }
      }
    }

    "the user hasn't logged in" must {
      "redirect the user to log in " in {
        val result = failingAuthAction(new MissingBearerToken).onPageLoad(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Redirect(
          frontendAppConfig.loginUrl,
          Map("continue" -> Seq(frontendAppConfig.loginContinueUrl), "origin" -> Seq(frontendAppConfig.appName))
        ).header.headers.get(LOCATION)
      }
    }

    "the user's session has expired" must {
      "redirect the user to log in " in {
        val result = failingAuthAction(new BearerTokenExpired).onPageLoad(fakeRequest)

        status(result)               mustBe SEE_OTHER
        redirectLocation(result).value must startWith(frontendAppConfig.loginUrl)
      }
    }

    "the user doesn't have sufficient enrolments" must {
      "redirect the user to the unauthorised page" in {
        val result = failingAuthAction(new InsufficientEnrolments).onPageLoad(fakeRequest)

        status(result)           mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad.url)
      }
    }

    "the user doesn't have sufficient confidence level" must {
      "redirect the user to the unauthorised page" in {
        val result = failingAuthAction(new InsufficientConfidenceLevel).onPageLoad(fakeRequest)

        status(result)           mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad.url)
      }
    }

    "the user used an unaccepted auth provider" must {
      "redirect the user to the unauthorised page" in {
        val result = failingAuthAction(new UnsupportedAuthProvider).onPageLoad(fakeRequest)

        status(result)           mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad.url)
      }
    }

    "the user has an unsupported affinity group" must {
      "redirect the user to the unauthorised page" in {
        val result = failingAuthAction(new UnsupportedAffinityGroup).onPageLoad(fakeRequest)

        status(result)           mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad.url)
      }
    }

    "the user has an unsupported credential role" must {
      "redirect the user to the unauthorised page" in {
        val result = failingAuthAction(new UnsupportedCredentialRole).onPageLoad(fakeRequest)

        status(result)           mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad.url)
      }
    }
  }

}
