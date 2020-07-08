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
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

trait CircuitBreakerRepo {
  def setCircuitBreaker(value: CircuitBreakerState): Future[Unit]

  def getCircuitBreakerStatus: Future[CircuitBreakerStatus]
}

@Singleton
class CircuitBreakerRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[CircuitBreakerStatus, BSONObjectID](
      "circuitBreaker",
      mongo.mongoConnector.db,
      CircuitBreakerStatus.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with CircuitBreakerRepo {

  private val id = "af38807c-c127-11ea-b3de-0242ac130004"

  private val defaultStatus = CircuitBreakerStatus(CircuitBreakerState.Closed, None, None)

  override def setCircuitBreaker(value: CircuitBreakerState): Future[Unit] =
    for {
      status <- getCircuitBreakerStatus
      _      <- doSetTo(value, status.circuitBreakerState)
    } yield ()

  private def doSetTo(value: CircuitBreakerState, current: CircuitBreakerState) =
    // FIXME race condition can happen if two threads (or two microservice instances) do this at same time.
    // how bad it it? (Is there a better way without  4.2 aggregation updates)
    if (current == value) {
      Future.successful(())
    } else {
      value match {
        case CircuitBreakerState.Open   => open()
        case CircuitBreakerState.Closed => close()
      }
    }

  private def open() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q = Json.obj("_id" -> id),
        u = Json.obj(
          "$set" -> Json.obj(
            "circuitBreakerState" -> "Open",
            "lastOpened"          -> Instant.now()
          )
        ),
        upsert = true
      )
      .map(_ => ())

  private def close() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q = Json.obj("_id" -> id),
        u = Json.obj(
          "$set" -> Json.obj(
            "circuitBreakerState" -> "Closed",
            "lastClosed"          -> Instant.now()
          )
        ),
        upsert = true
      )
      .map(_ => ())

  override def getCircuitBreakerStatus: Future[CircuitBreakerStatus] =
    collection
      .find(selector = Json.obj("_id" -> id), projection = Option.empty[JsObject])
      .one[CircuitBreakerStatus](ReadPreference.primaryPreferred)
      .map(_.getOrElse(defaultStatus))
}
