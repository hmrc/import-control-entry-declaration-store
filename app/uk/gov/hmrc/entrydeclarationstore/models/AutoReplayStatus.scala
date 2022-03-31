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

package uk.gov.hmrc.entrydeclarationstore.models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import java.time.Instant
import uk.gov.hmrc.entrydeclarationstore.models.ReplayState.Implicits._

sealed trait AutoReplayStatus {
  val lastReplay: Option[ReplayState]
}

object AutoReplayStatus {

  case class On(val lastReplay: Option[ReplayState]) extends AutoReplayStatus
  case class Off(val lastReplay: Option[ReplayState]) extends AutoReplayStatus
  case object Unavailable extends AutoReplayStatus {
    val lastReplay: Option[ReplayState] = None
  }

  def buildStatus(autoReplay: Option[Boolean], lastReplay: Option[ReplayState]): AutoReplayStatus =
    autoReplay match {
      case Some(true) => On(lastReplay)
      case Some(false) => Off(lastReplay)
      case None => Unavailable
    }

  val reads: Reads[AutoReplayStatus] =
    (
      (JsPath \ "autoReplay").readNullable[Boolean] and
      (JsPath \ "lastReplay").readNullable[ReplayState]
    )(buildStatus _)

  val writes: OWrites[AutoReplayStatus] = {

    val unpackStatus: AutoReplayStatus => (Option[Boolean], Option[ReplayState]) = {
      case On(lastReplay)        => (Some(true), lastReplay)
      case Off(lastReplay)        => (Some(false), lastReplay)
      case Unavailable => (None, None)
    }

    ((__ \ "autoReplay").writeNullable[Boolean] and
      (__ \ "lastReplay").writeNullable[ReplayState])(unpackStatus)
  }

  implicit val format: Format[AutoReplayStatus] = Format(reads, writes)
}

case class LastReplay(id: Option[String], when: Instant)

case class AutoReplayRepoStatus(autoReplay: Boolean, lastReplay: Option[LastReplay])

object AutoReplayRepoStatus {
  import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._

  implicit val resultsFormat: Format[LastReplay] = Json.format[LastReplay]
  implicit val format: Format[AutoReplayRepoStatus] = Json.format[AutoReplayRepoStatus]
}

