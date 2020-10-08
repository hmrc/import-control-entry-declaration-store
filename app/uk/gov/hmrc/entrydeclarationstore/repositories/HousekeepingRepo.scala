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

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.WriteConcern
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait HousekeepingRepo {
  def enableHousekeeping(value: Boolean): Future[Unit]

  def getHousekeepingStatus: Future[HousekeepingStatus]
}

@Singleton
class HousekeepingRepoImpl @Inject()(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[HousekeepingStatus, BSONObjectID](
      "houskeeping-status",
      mongo.mongoConnector.db,
      HousekeepingStatus.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with HousekeepingRepo {

  private val singletonId = "1d4165fc-3a66-4f13-b067-ac7e087aab73"

  override def enableHousekeeping(value: Boolean): Future[Unit] =
    if (value) turnOn() else turnOff()

  private def turnOn() =
    remove("_id" -> singletonId)
      .andThen {
        case Success(_) => Logger.warn("Housekeeping turned on")
      }
      .map(_ => ())

  private def turnOff() =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q      = Json.obj("_id" -> singletonId),
        u      = JsObject.empty,
        upsert = true
      )
      .andThen {
        case Success(_) => Logger.warn("Housekeeping turned off")
      }
      .map(_ => ())

  override def getHousekeepingStatus: Future[HousekeepingStatus] =
    count(Json.obj("_id" -> singletonId))
      .map(n => HousekeepingStatus(n == 0))
}
