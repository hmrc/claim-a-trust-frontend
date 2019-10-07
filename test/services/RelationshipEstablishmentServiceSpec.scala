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

package services

import base.SpecBase
import controllers.actions.{FakeAuthConnector, FakeFailingAuthConnector}
import controllers.routes
import play.api.mvc.{AnyContent, Request, Results}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{FailedRelationship, MissingBearerToken}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{ExecutionContext, Future}

class RelationshipEstablishmentServiceSpec extends SpecBase {

  val utr = "1234567890"

  def harness = (request: Request[AnyContent]) => Future.successful(Results.Ok)

  implicit val ec = implicitly[ExecutionContext]

  "RelationshipEstablishment" when {

    "the user hasn't logged in" must {

      "redirect the user to log in " in {

        val auth = new FakeFailingAuthConnector(new MissingBearerToken)

        val service = new RelationshipEstablishmentService(auth)

        val result = service.check(fakeInternalId, utr)(harness)

        status(result) mustBe SEE_OTHER

        redirectLocation(result).get must startWith(frontendAppConfig.loginUrl)
      }
    }

    "the user has logged in" when {

        "where no relationship exists" must {

          "continue the request action" in {

            val auth = new FakeFailingAuthConnector(new FailedRelationship())

            val service = new RelationshipEstablishmentService(auth)

            val result = service.check(fakeInternalId, utr)(harness)

            status(result) mustBe OK
          }

        }

      "where a relationship exists" must {

        "redirect to success page" in {

          val auth = new FakeAuthConnector(Future.successful())

          val service = new RelationshipEstablishmentService(auth)

          val result = service.check(fakeInternalId, utr)(harness)

          status(result) mustBe SEE_OTHER

          redirectLocation(result).value mustBe routes.IvSuccessController.onPageLoad().url

        }

      }

    }

  }

}
