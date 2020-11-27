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
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

trait TrafficSwitchRepo {
  def setTrafficSwitchState(value: TrafficSwitchState): Future[Option[TrafficSwitchStatus]]

  def getTrafficSwitchStatus: Future[TrafficSwitchStatus]

  def resetToDefault: Future[Unit]
}

@Singleton
class TrafficSwitchRepoImpl @Inject()(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[TrafficSwitchStatus, BSONObjectID](
      "trafficSwitch",
      mongo.mongoConnector.db,
      TrafficSwitchStatus.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with TrafficSwitchRepo {

  private val singletonId = "af38807c-c127-11ea-b3de-0242ac130004"

  val defaultStatus: TrafficSwitchStatus = TrafficSwitchStatus(TrafficSwitchState.Flowing, None, None)

  override def setTrafficSwitchState(value: TrafficSwitchState): Future[Option[TrafficSwitchStatus]] = for {
      _ <- insertDefaultIfEmpty()
      result <- value match {
        case TrafficSwitchState.Flowing => startTrafficFlow
        case TrafficSwitchState.NotFlowing => stopTrafficFlow
      }
    } yield result

  // Note: Pre Mongo 4.2 we cannot update fields conditionally so we have to split not flowing and flowing methods.
  // We cannot upsert in these (since match failure would attempt insert and violate unique _id).
  // Hence this, which will insert only when the singleton document is missing, allows not flowing/flowing without
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

  private def stopTrafficFlow =
    collection
      .findAndUpdate[JsObject, JsObject](
        selector = Json.obj("_id" -> singletonId, "isTrafficFlowing" -> TrafficSwitchState.Flowing),
        update =
          Json.obj("$set" -> Json.obj("isTrafficFlowing" -> TrafficSwitchState.NotFlowing, "lastTrafficStopped" -> Instant.now)),
        fetchNewObject           = true,
        upsert                   = false,
        sort                     = None,
        fields                   = None,
        bypassDocumentValidation = false,
        writeConcern             = WriteConcern.Default,
        maxTime                  = None,
        collation                = None,
        arrayFilters             = Seq.empty
      ).map(_.result[TrafficSwitchStatus])

  private def startTrafficFlow =
    collection
      .findAndUpdate[JsObject, JsObject](
        selector = Json.obj("_id" -> singletonId, "isTrafficFlowing" -> TrafficSwitchState.NotFlowing),
        update =
          Json.obj("$set" -> Json.obj("isTrafficFlowing" -> TrafficSwitchState.Flowing, "lastTrafficStarted" -> Instant.now)),
        fetchNewObject           = true,
        upsert                   = false,
        sort                     = None,
        fields                   = None,
        bypassDocumentValidation = false,
        writeConcern             = WriteConcern.Default,
        maxTime                  = None,
        collation                = None,
        arrayFilters             = Seq.empty
      ).map(_.result[TrafficSwitchStatus])

  override def getTrafficSwitchStatus: Future[TrafficSwitchStatus] =
    collection
      .find(selector = Json.obj("_id" -> singletonId), projection = Option.empty[JsObject])
      .one[TrafficSwitchStatus](ReadPreference.primaryPreferred)
      .map(_.getOrElse(defaultStatus))

  override def resetToDefault: Future[Unit] = removeAll(WriteConcern.Default).map(_ => ())
}
