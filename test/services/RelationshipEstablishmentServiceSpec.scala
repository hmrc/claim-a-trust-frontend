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

package services

import java.util.concurrent.TimeUnit

import base.SpecBase
import controllers.actions.{FakeAuthConnector, FakeFailingAuthConnector}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.auth.core.{FailedRelationship, MissingBearerToken}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RelationshipEstablishmentServiceSpec extends SpecBase with ScalaFutures {

  val utr = "1234567890"

  "RelationshipEstablishment" when {

    "the user hasn't logged in" must {

      "throw an exception" in {

        val auth = new FakeFailingAuthConnector(new MissingBearerToken)

        val service = new RelationshipEstablishmentService(auth)

        intercept[RelationshipError] {
          Await.result(service.check(fakeInternalId, utr), Duration(5, TimeUnit.SECONDS))
        }
      }
    }

    "the user has logged in" when {

        "where no relationship exists" must {

          "return RelationshipNotFound" in {

            val auth = new FakeFailingAuthConnector(FailedRelationship())

            val service = new RelationshipEstablishmentService(auth)

            val result = service.check(fakeInternalId, utr)

            whenReady(result) {
              s =>
                s mustBe RelationshipNotFound
            }
          }

        }

      "where a relationship exists" must {

        "return RelationshipFound" in {

          val auth = new FakeAuthConnector(Future.successful(()))

          val service = new RelationshipEstablishmentService(auth)

          val result = service.check(fakeInternalId, utr)

          whenReady(result) {
            s =>
              s mustBe RelationshipFound
          }

        }

      }

    }

  }

}
