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

import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayTrigger, ReplayState}
import uk.gov.hmrc.play.http.logging.Mdc
import java.util.concurrent.TimeUnit
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait ReplayStateRepo {
  def list(count: Option[Int]): Future[List[ReplayState]]
  def lookupState(replayId: String): Future[Option[ReplayState]]
  def setState(replayState: ReplayState): Future[Unit]
  def lookupIdOfLatest: Future[Option[String]]
  def mostRecentByTrigger(trigger: ReplayTrigger): Future[Option[ReplayState]]
  def insert(replayId: String, trigger: ReplayTrigger, totalToReplay: Int, startTime: Instant): Future[Unit]
  def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): Future[Boolean]
  def setCompleted(replayId: String, completed: Boolean, endTime: Instant): Future[Boolean]
}

@Singleton
class ReplayStateRepoImpl @Inject()
  (config: AppConfig)
  (implicit mongo: MongoComponent,
   ec: ExecutionContext) extends PlayMongoRepository[ReplayState](
  collectionName = "replayState",
  mongoComponent = mongo,
  domainFormat = ReplayState.MongoImplicits.mongoFormat,
  indexes = Seq(IndexModel(ascending("replayId"),
                           IndexOptions()
                            .name("replayIdIndex")
                            .unique(true)),
                IndexModel(ascending("startTime"),
                           IndexOptions()
                            .name("expiryIndex")
                            .unique(false)
                            .expireAfter(config.replayStateLifetime.toSeconds, TimeUnit.SECONDS))),
  extraCodecs = Seq(Codecs.playFormatCodec(MongoFormats.objectIdFormat),
                    Codecs.playFormatCodec(ReplayTrigger.formats)),
  replaceIndexes = true)
    with ReplayStateRepo {

  //
  // Test support FNs
  //
  def removeAll(): Future[Unit] =
    collection
      .deleteMany(exists("_id"))
      .toFutureOption()
      .map( _ => ())

  def list(count: Option[Int]): Future[List[ReplayState]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find()
          .sort(descending("startTime"))
          .limit(count.getOrElse(0))
          .collect()
          .toFutureOption()
      )
      .map(_.fold[List[ReplayState]](Nil)(_.toList))

  def mostRecentByTrigger(trigger: ReplayTrigger): Future[Option[ReplayState]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find(equal("trigger", Codecs.toBson(trigger)))
          .sort(descending("startTime"))
          .headOption()
      )

  override def lookupState(replayId: String): Future[Option[ReplayState]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find(equal("replayId", replayId))
          .headOption()
      )

  override def lookupIdOfLatest: Future[Option[String]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find()
          .sort(descending("startTime"))
          .headOption()
      )
      .map{
        case Some(id) => Some(id.replayId)
        case _ => None
      }

override def setState(replayState: ReplayState): Future[Unit] =
    Mdc
      .preservingMdc(
        collection
          .findOneAndReplace(equal("replayId", replayState.replayId),
                             replayState,
                             FindOneAndReplaceOptions().upsert(true))
          .headOption()
      )
      .map(_ => ())

  override def insert(replayId: String, trigger: ReplayTrigger, totalToReplay: Int, startTime: Instant): Future[Unit] =
    Mdc
      .preservingMdc(
        collection
          .insertOne(ReplayState(replayId, startTime, totalToReplay, trigger))
          .toFutureOption()
      )
      .map(_ => ())

  override def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(equal("replayId", replayId),
                     combine(inc("successCount", successesToAdd),
                             inc("failureCount", failuresToAdd)))
          .toFutureOption()
      )
      .map{
        result => result.map(_.getModifiedCount > 0).getOrElse(false)
      }

  override def setCompleted(replayId: String, completed: Boolean, endTime: Instant): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(equal("replayId", replayId),
                    combine(set("completed", completed), set("endTime", endTime)))
          .headOption()
      )
      .map(_.map(_.getModifiedCount > 0).getOrElse(false))
}
