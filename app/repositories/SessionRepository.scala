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

package repositories

import cats.data.EitherT
import com.google.inject.ImplementedBy
import config.FrontendAppConfig
import errors.ServerError
import models.UserAnswers
import org.mongodb.scala.MongoServerException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model._
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import utils.TrustEnvelope.TrustEnvelope

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class DefaultSessionRepository @Inject() (val mongo: MongoComponent, val config: FrontendAppConfig)(implicit
  val ec: ExecutionContext
) extends PlayMongoRepository[UserAnswers](
      collectionName = "user-answers",
      mongoComponent = mongo,
      domainFormat = Format(UserAnswers.reads, UserAnswers.writes),
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("user-answers-last-updated-index")
            .expireAfter(config.cachettlInSeconds, TimeUnit.SECONDS)
            .unique(false)
        )
      ),
      replaceIndexes = config.dropIndexes
    )
    with SessionRepository
    with Logging {
  private val className = this.getClass.getSimpleName

  override def get(id: String): TrustEnvelope[Option[UserAnswers]] = EitherT {

    val selector = Filters.equal("_id", id)
    collection.find(selector).headOption().map(Right(_)).recover {
      case e: MongoServerException =>
        logger.error(s"[$className][set] failed to fetch from $collectionName ${e.getMessage}")
        Left(ServerError(e.getMessage))
      case e: Exception            =>
        logger.error(s"[$className][set] $collectionName ${e.getMessage}")
        Left(ServerError(e.getMessage))
    }
  }

  override def set(userAnswers: UserAnswers): TrustEnvelope[Boolean] = EitherT {

    val selector       = equal("_id", userAnswers.id)
    val newUser        = userAnswers.copy(lastUpdated = Instant.now())
    val replaceOptions = new ReplaceOptions().upsert(true)

    collection
      .replaceOne(selector, newUser, replaceOptions)
      .headOption()
      .map { result =>
        Right(result.exists(_.wasAcknowledged()))
      }
      .recover {
        case e: MongoServerException =>
          logger.error(s"[$className][set] failed to insert to $collectionName ${e.getMessage}")
          Left(ServerError(e.getMessage))
        case e: Exception            =>
          logger.error(s"[$className][set] $collectionName ${e.getMessage}")
          Left(ServerError(e.getMessage))
      }
  }

}

@ImplementedBy(classOf[DefaultSessionRepository])
trait SessionRepository {

  def get(id: String): TrustEnvelope[Option[UserAnswers]]

  def set(userAnswers: UserAnswers): TrustEnvelope[Boolean]
}
