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

package uk.gov.hmrc.entrydeclarationstore.repositories

import play.api.Logger
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.play.http.logging.Mdc

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait HousekeepingRepo {
  def enableHousekeeping(value: Boolean): Future[Unit]

  def getHousekeepingStatus: Future[HousekeepingStatus]
}

@Singleton
class HousekeepingRepoImpl @Inject()(
  implicit mongo: MongoComponent,
  ec: ExecutionContext
) extends PlayMongoRepository[HousekeepingStatus](
      collectionName = "houskeeping-status",
      mongoComponent = mongo,
      domainFormat = HousekeepingStatus.format,
      indexes = Seq.empty,
      extraCodecs = Seq(Codecs.playFormatCodec(MongoFormats.objectIdFormat)),
      replaceIndexes = true
    )
    with HousekeepingRepo {

  val logger: Logger = Logger(getClass)
  private val singletonId = "1d4165fc-3a66-4f13-b067-ac7e087aab73"

  //
  // Test support FNs
  //
  def removeAll(): Future[Unit] =
    collection
      .deleteMany(exists("_id"))
      .toFutureOption()
      .map( _ => ())

  override def enableHousekeeping(value: Boolean): Future[Unit] =
    if (value) turnOn() else turnOff()

  private def turnOn() =
    Mdc
      .preservingMdc(collection.deleteOne(equal("_id", singletonId)).toFutureOption())
      .andThen {
        case Success(_) => logger.warn("Housekeeping turned on")
      }
      .map(_ => ())

  private def turnOff() =
    Mdc
      .preservingMdc(
        collection
          .updateOne(equal("_id", singletonId), set("_id", singletonId), UpdateOptions().upsert(true))
          .toFutureOption()
      )
      .andThen {
        case Success(_) => logger.warn("Housekeeping turned off")
      }
      .map(_ => ())

  override def getHousekeepingStatus: Future[HousekeepingStatus] =
    Mdc
      .preservingMdc(collection.countDocuments(equal("_id", singletonId)).toFutureOption())
      .map{
        case Some(n) => HousekeepingStatus(n == 0)
        case _ => HousekeepingStatus(false)
      }
}
