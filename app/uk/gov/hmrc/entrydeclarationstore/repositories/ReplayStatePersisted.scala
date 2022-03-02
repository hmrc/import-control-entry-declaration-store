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

import java.time.Instant
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayTrigger, ReplayState}

private[repositories] case class ReplayStatePersisted(
  replayId: String,
  startTime: Instant,
  totalToReplay: Int,
  trigger: Option[ReplayTrigger] = Some(ReplayTrigger.Manual),
  completed: Boolean                   = false,
  endTime: Option[Instant] = None,
  successCount: Int                    = 0,
  failureCount: Int                    = 0
) {
  def toDomain: ReplayState =
    ReplayState(
      trigger       = trigger.getOrElse(ReplayTrigger.Manual),
      startTime     = startTime,
      endTime       = endTime,
      completed     = completed,
      successCount  = successCount,
      failureCount  = failureCount,
      totalToReplay = totalToReplay
    )
}

private[repositories] object ReplayStatePersisted {
  implicit val format: Format[ReplayStatePersisted] = Json.format[ReplayStatePersisted]

  def fromDomain(replayId: String, replayState: ReplayState): ReplayStatePersisted = {
    import replayState._
    ReplayStatePersisted(
      replayId,
      trigger       = Some(trigger),
      startTime     = startTime,
      endTime       = endTime,
      completed     = completed,
      successCount  = successCount,
      failureCount  = failureCount,
      totalToReplay = totalToReplay
    )
  }
}
