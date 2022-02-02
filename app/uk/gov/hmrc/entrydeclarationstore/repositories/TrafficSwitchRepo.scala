/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus, InstantFormatter}
import uk.gov.hmrc.play.http.logging.Mdc
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait TrafficSwitchRepo {
  def setTrafficSwitchState(value: TrafficSwitchState): Future[Option[TrafficSwitchStatus]]
  def getTrafficSwitchStatus: Future[TrafficSwitchStatus]
  def resetToDefault: Future[Unit]
}

@Singleton
class TrafficSwitchRepoImpl @Inject()(
  implicit mongo: MongoComponent,
  ec: ExecutionContext
) extends PlayMongoRepository[TrafficSwitchStatus](
  collectionName = "trafficSwitch",
  mongoComponent = mongo,
  domainFormat = TrafficSwitchStatus.format,
  indexes = Seq.empty,
  extraCodecs = Seq(Codecs.playFormatCodec(TrafficSwitchState.formats),
                    Codecs.playFormatCodec(InstantFormatter.instantWrites)),
  replaceIndexes = true) with TrafficSwitchRepo  {
  import TrafficSwitchState.mongoFormatString

  private val singletonId = "af38807c-c127-11ea-b3de-0242ac130004"

  val defaultStatus: TrafficSwitchStatus = TrafficSwitchStatus(TrafficSwitchState.Flowing, None, None)

  override def setTrafficSwitchState(value: TrafficSwitchState): Future[Option[TrafficSwitchStatus]] =
    for {
      _ <- insertDefaultIfEmpty()
      result <- value match {
                 case TrafficSwitchState.Flowing    => startTrafficFlow
                 case TrafficSwitchState.NotFlowing => stopTrafficFlow
               }
    } yield result

  // Note: Pre Mongo 4.2 we cannot update fields conditionally so we have to split not flowing and flowing methods.
  // We cannot upsert in these (since match failure would attempt insert and violate unique _id).
  // Hence this, which will insert only when the singleton document is missing, allows not flowing/flowing without
  // upsert or the need to do racy 'check-then-act' logic:
  private def insertDefaultIfEmpty() =
    Mdc.preservingMdc(
      collection
        .updateOne(
          equal("_id", singletonId),
          combine(
            set("_id", singletonId),
            setOnInsert("isTrafficFlowing", mongoFormatString(defaultStatus.isTrafficFlowing))
          ),
          UpdateOptions().upsert(true)
          )
        .toFutureOption
    )

  private def stopTrafficFlow =
    Mdc
      .preservingMdc(
        collection
          .findOneAndUpdate(
            and(equal("_id", singletonId), equal("isTrafficFlowing", mongoFormatString(TrafficSwitchState.Flowing))),
            combine(
              set("isTrafficFlowing", mongoFormatString(TrafficSwitchState.NotFlowing)),
              set("lastTrafficStopped", Instant.now)
            ),
            FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER).bypassDocumentValidation(false)
          )
          .toFutureOption
      )

  private def startTrafficFlow =
    Mdc
      .preservingMdc(
        collection
          .findOneAndUpdate(
            and(equal("_id", singletonId), equal("isTrafficFlowing", mongoFormatString(TrafficSwitchState.NotFlowing))),
            combine(
              set("isTrafficFlowing", mongoFormatString(TrafficSwitchState.Flowing)),
              set("lastTrafficStarted", Instant.now)
            ),
            FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER).bypassDocumentValidation(false)
          )
          .toFutureOption
      )

  override def getTrafficSwitchStatus: Future[TrafficSwitchStatus] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred)
          .find(equal("_id", singletonId))
          .headOption
      )
      .map(_.getOrElse(defaultStatus))

  override def resetToDefault: Future[Unit] =
    Mdc.preservingMdc(removeAll)

  def removeAll: Future[Unit] =
    collection
      .deleteMany(exists("_id"))
      .toFutureOption
      .map( _ => ())
}
