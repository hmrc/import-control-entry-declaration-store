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

package uk.gov.hmrc.entrydeclarationstore.repositories

import play.api.libs.json.{JsObject, JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ReadPreference, WriteConcern}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationstore.models.ReplayState
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait ReplayStateRepo {
  def lookupState(replayId: String): Future[Option[ReplayState]]

  def lookupIdOfLatest: Future[Option[String]]

  def setState(replayId: String, replayState: ReplayState): Future[Unit]

  def insert(replayId: String, totalToReplay: Int, startTime: Instant): Future[Unit]

  def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): Future[Boolean]

  def setCompleted(replayId: String, endTime: Instant): Future[Boolean]
}

@Singleton
class ReplayStateRepoImpl @Inject()(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext
) extends ReactiveRepository[ReplayStatePersisted, BSONObjectID](
      "replayState",
      mongo.mongoConnector.db,
      ReplayStatePersisted.format,
      ReactiveMongoFormats.objectIdFormats)
    with ReplayStateRepo {

  override def indexes: Seq[Index] =
    Seq(Index(Seq(("replayId", Ascending)), name = Some("replayIdIndex"), unique = true))

  override def lookupState(replayId: String): Future[Option[ReplayState]] =
    collection
      .find(Json.obj("replayId" -> replayId), Option.empty[JsObject])
      .one[ReplayStatePersisted](ReadPreference.primaryPreferred)
      .map(_.map(_.toDomain))

  override def lookupIdOfLatest: Future[Option[String]] =
    collection
      .find(JsObject.empty, Some(Json.obj("replayId" -> 1)))
      .sort(Json.obj("startTime" -> -1))
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map(doc => (doc \ "replayId").as[String]))

  override def setState(replayId: String, replayState: ReplayState): Future[Unit] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        q      = Json.obj("replayId" -> replayId),
        u      = Json.toJson(ReplayStatePersisted.fromDomain(replayId, replayState)).as[JsObject],
        upsert = true
      )
      .map(_ => ())

  override def insert(replayId: String, totalToReplay: Int, startTime: Instant): Future[Unit] =
    insert(ReplayStatePersisted(replayId, PersistableDateTime(startTime), totalToReplay))
      .map(_ => ())

  override def incrementCounts(replayId: String, successesToAdd: Int, failuresToAdd: Int): Future[Boolean] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        Json.obj("replayId" -> replayId),
        Json.obj("$inc"     -> Json.obj("successCount" -> successesToAdd, "failureCount" -> failuresToAdd))
      )
      .map(result => result.nModified > 0)

  override def setCompleted(replayId: String, endTime: Instant): Future[Boolean] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        Json.obj("replayId" -> replayId),
        Json.obj("$set"     -> Json.obj("completed" -> true, "endTime" -> PersistableDateTime(endTime)))
      )
      .map(result => result.nModified > 0)
}
