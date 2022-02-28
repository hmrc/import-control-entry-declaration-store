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

import play.api.Logging
import play.api.libs.json.{Format, Json}
import java.time.Instant
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import org.mongodb.scala.bson.conversions.Bson
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.entrydeclarationstore.models.AutoReplayStatus
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AutoReplayRepository {
  def startAutoReplay(): Future[Unit]
  def stopAutoReplay(): Future[Unit]
  def getAutoReplayStatus(): Future[AutoReplayStatus]
  def setLastRepay(replayId: Option[String], when: Instant = Instant.now): Future[AutoReplayRepoStatus]
}

case class Replay(id: Option[String], when: Instant)
case class AutoReplayRepoStatus(autoReplay: Boolean, lastReplay: Option[Replay])
object AutoReplayRepoStatus {
  implicit val resultsFormat: Format[Replay] = Json.format[Replay]
  implicit val format: Format[AutoReplayRepoStatus] = Json.format[AutoReplayRepoStatus]
}

@Singleton
class AutoReplayRepositoryImpl @Inject()(
  implicit mongo: MongoComponent,
  ec: ExecutionContext
) extends PlayMongoRepository[AutoReplayRepoStatus](
      collectionName = "auto-replay-status",
      mongoComponent = mongo,
      domainFormat = AutoReplayRepoStatus.format,
      indexes = Seq.empty
    )
    with AutoReplayRepository with Logging {

  import AutoReplayStatus._
  private val singletonId = "2fe7847b-5922-455c-99fb-8bcce811c37"

  def removeAll(): Future[Unit] = startAutoReplay() // Test support FN

  def getAutoReplayStatus(): Future[AutoReplayStatus] =
    Mdc.preservingMdc(
      collection
        .find(equal("_id", singletonId))
        .headOption
        .map{
          case Some(AutoReplayRepoStatus(true, _)) => On
          case Some(AutoReplayRepoStatus(false, _)) => Off
          case _ => On
        }
    )

  def setLastRepay(replayId: Option[String], when: Instant = Instant.now): Future[AutoReplayRepoStatus] ={
    val update: Bson = replayId.fold(set("lastReplay.when", when)){id =>
      combine(set("lastReplay.id", id),
              set("lastReplay.when", when))
    }

    Mdc.preservingMdc(
      collection
        .findOneAndUpdate(equal("_id", singletonId),
                          update,
                          FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER))
        .headOption
        .map{
          case None => AutoReplayRepoStatus(true, None)
          case Some(status) => status
        }
    )
  }

  def startAutoReplay(): Future[Unit] =
    Mdc.preservingMdc{
      collection
        .updateOne(equal("_id", singletonId), set("autoReplay", true), UpdateOptions().upsert(true))
        .toFutureOption
        .map(_ => ())
      //collection.deleteOne(equal("_id", singletonId)).toFutureOption.map(_ => ())
    }

  def stopAutoReplay(): Future[Unit] =
    Mdc.preservingMdc{
      collection
        .updateOne(equal("_id", singletonId), set("autoReplay", false), UpdateOptions().upsert(true))
        .toFutureOption
        .map(_ => ())
    }

}
