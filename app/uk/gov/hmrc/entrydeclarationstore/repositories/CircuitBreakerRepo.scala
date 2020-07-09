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

package uk.gov.hmrc.entrydeclarationstore.repositories

import java.time.Instant

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{ReadPreference, WriteConcern}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

trait CircuitBreakerRepo {
  def setCircuitBreaker(value: CircuitBreakerState): Future[Unit]

  def getCircuitBreakerStatus: Future[CircuitBreakerStatus]

  def resetToDefault: Future[Unit] = ???
}

@Singleton
class CircuitBreakerRepoImpl @Inject()(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[CircuitBreakerStatus, BSONObjectID](
      "circuitBreaker",
      mongo.mongoConnector.db,
      CircuitBreakerStatus.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with CircuitBreakerRepo {
  import CircuitBreakerState._

  private val singletonId = "af38807c-c127-11ea-b3de-0242ac130004"

  private val defaultStatus = CircuitBreakerStatus(Closed, None, None)

  override def setCircuitBreaker(value: CircuitBreakerState): Future[Unit] =
    for {
      _ <- insertDefaultIfEmpty()
      _ <- value match {
            case Open   => open()
            case Closed => close()
          }
    } yield ()

  // Note: Pre Mongo 4.2 we cannot update fields conditionally so we have to split open and close methods.
  // We cannot upsert in these (since match failure would attempt insert and voilate unique _id).
  // Hence this, which will insert only when the singleton document is missing, allows open/close without
  // upsert or the need to do racy 'check-then-act' logic:
  private def insertDefaultIfEmpty() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q = Json.obj("_id" -> singletonId),
        u = Json.obj(
          "$setOnInsert" -> defaultStatus
        ),
        upsert = true
      )

  private def open() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q = Json.obj("_id" -> singletonId, "circuitBreakerState" -> Closed),
        u = Json.obj(
          "$set" -> Json.obj(
            "circuitBreakerState" -> Open,
            "lastOpened"          -> Instant.now()
          )
        )
      )

  private def close() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q = Json.obj("_id" -> singletonId, "circuitBreakerState" -> Open),
        u = Json.obj(
          "$set" -> Json.obj(
            "circuitBreakerState" -> Closed,
            "lastClosed"          -> Instant.now()
          )
        )
      )
      .map(_ => ())

  override def getCircuitBreakerStatus: Future[CircuitBreakerStatus] =
    collection
      .find(selector = Json.obj("_id" -> singletonId), projection = Option.empty[JsObject])
      .one[CircuitBreakerStatus](ReadPreference.primaryPreferred)
      .map(_.getOrElse(defaultStatus))
}
