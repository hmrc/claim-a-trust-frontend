/*
 * Copyright 2021 HM Revenue & Customs
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

import models.UserAnswers
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.bson.collection.BSONSerializationPack
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.bson.BSONDocument
import reactivemongo.play.json.collection.Helpers.idWrites
import reactivemongo.play.json.collection.JSONCollection

import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultSessionRepository @Inject()(
                                          val mongo: ReactiveMongoApi,
                                          val config: Configuration
                                        )(implicit val ec: ExecutionContext) extends SessionRepository with IndexManager {

  override val collectionName: String = "user-answers"

  private val lastUpdatedIndexKey = "lastUpdated"
  private val lastUpdatedIndexName = "user-answers-last-updated-index"

  private val cacheTtl = config.get[Int]("mongodb.timeToLiveInSeconds")

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val lastUpdatedIndex = Index.apply(BSONSerializationPack)(
    key = Seq(lastUpdatedIndexKey -> IndexType.Ascending),
    name = Some(lastUpdatedIndexName),
    expireAfterSeconds = Some(cacheTtl),
    options = BSONDocument.empty,
    unique = false,
    background = false,
    dropDups = false,
    sparse = false,
    version = None,
    partialFilter = None,
    storageEngine = None,
    weights = None,
    defaultLanguage = None,
    languageOverride = None,
    textIndexVersion = None,
    sphereIndexVersion = None,
    bits = None,
    min = None,
    max = None,
    bucketSize = None,
    collation = None,
    wildcardProjection = None
  )

  def ensureTtlIndex(collection: JSONCollection): Future[Unit] = {
    collection.indexesManager.ensure(lastUpdatedIndex) flatMap {
      newlyCreated =>
        // false if the index already exists
        if (!newlyCreated) {
          for {
            _ <- collection.indexesManager.drop(lastUpdatedIndexName)
            _ <- collection.indexesManager.ensure(lastUpdatedIndex)
          } yield ()
        } else {
          Future.successful(())
        }
    }
  }

  val started: Future[Unit] =
    collection.flatMap(ensureTtlIndex).map(_ => ())

  override def get(id: String): Future[Option[UserAnswers]] =
    collection.flatMap(_.find(Json.obj("_id" -> id), None).one[UserAnswers])

  override def set(userAnswers: UserAnswers): Future[Boolean] = {

    val selector = Json.obj(
      "_id" -> userAnswers.id
    )

    val modifier = Json.obj(
      "$set" -> (userAnswers copy (lastUpdated = LocalDateTime.now))
    )

    collection.flatMap {
      _.update(ordered = false)
        .one(selector, modifier, upsert = true).map {
          lastError =>
            lastError.ok
      }
    }
  }
}

trait SessionRepository {

  val started: Future[Unit]

  def get(id: String): Future[Option[UserAnswers]]

  def set(userAnswers: UserAnswers): Future[Boolean]
}
