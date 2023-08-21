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

package uk.gov.hmrc.entrydeclarationstore.models

import play.api.libs.json.{Format, Json, __, Reads, OWrites}
import play.api.libs.functional.syntax._
import java.time.Instant

case class ReplayState(
  replayId: String,
  startTime: Instant,
  totalToReplay: Int,
  trigger: ReplayTrigger,
  completed: Option[Boolean] = None,
  endTime: Option[Instant] = None,
  successCount: Int = 0,
  failureCount: Int = 0)

object ReplayState {

  def build(id: String,
            start: Instant,
            total: Int,
            trigger: Option[ReplayTrigger],
            completed: Option[Boolean],
            end: Option[Instant],
            success: Int,
            failure: Int): ReplayState =
    ReplayState(id, start, total, trigger.getOrElse(ReplayTrigger.Manual), completed, end, success, failure)

  object Implicits {
    implicit val replayStateFormat: Format[ReplayState] = Json.format[ReplayState]
  }

  object MongoImplicits {
    import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
    val reads: Reads[ReplayState] = (
        (__ \ "replayId").read[String] and
        (__ \ "startTime").read[Instant] and
        (__ \ "totalToReplay").read[Int] and
        (__ \ "trigger").readNullable[ReplayTrigger] and
        (__ \ "completed").readNullable[Boolean] and
        (__ \ "endTime").readNullable[Instant] and
        (__ \ "successCount").read[Int] and
        (__ \ "failureCount").read[Int]
      )(build _)

    val writes: OWrites[ReplayState] = (
        (__ \ "replayId").write[String] and
        (__ \ "startTime").write[Instant] and
        (__ \ "totalToReplay").write[Int] and
        (__ \ "trigger").write[ReplayTrigger] and
        (__ \ "completed").writeNullable[Boolean] and
        (__ \ "endTime").writeNullable[Instant] and
        (__ \ "successCount").write[Int] and
        (__ \ "failureCount").write[Int]
      )(unlift(ReplayState.unapply))
    implicit val mongoFormat: Format[ReplayState] = Format(reads, writes)
  }
}
