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

import play.api.Logging
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.entrydeclarationstore.models._

trait AutoReplayRepository {
  def start(): Future[Unit]
  def stop(): Future[Unit]
  def getStatus(): Future[Option[AutoReplayRepoStatus]]
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

  private val singletonId = "2fe7847b-5922-455c-99fb-8bcce811c37"

  def removeAll(): Future[Unit] =
    collection
      .deleteOne(equal("_id", singletonId))
      .headOption()
      .map( _ => ())

  def getStatus(): Future[Option[AutoReplayRepoStatus]] =
    Mdc.preservingMdc(
      collection
        .find(equal("_id", singletonId))
        .headOption
        .map{
          case None => Some(AutoReplayRepoStatus(true))
          case status => status
        }
        .recover{
          case err =>
            logger.error(s"Error retrieving auto-replay status: $err")
            None
        }
    )

  def start(): Future[Unit] =
    Mdc.preservingMdc{
      collection
        .updateOne(equal("_id", singletonId), set("autoReplay", true), UpdateOptions().upsert(true))
        .toFutureOption
        .map(_ => ())
        .recover{
          case err => logger.error(s"Error attempting to start auto-replay: $err")
        }
    }

  def stop(): Future[Unit] =
    Mdc.preservingMdc{
      collection
        .updateOne(equal("_id", singletonId), set("autoReplay", false), UpdateOptions().upsert(true))
        .toFutureOption
        .map(_ => ())
        .recover{
          case err => logger.error(s"Error attempting to stop auto-replay: $err")
        }

    }

}
