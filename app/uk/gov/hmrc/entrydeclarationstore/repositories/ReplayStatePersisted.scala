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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.entrydeclarationstore.models.ReplayState

private[repositories] case class ReplayStatePersisted(
  replayId: String,
  startTime: PersistableDateTime,
  totalToReplay: Int,
  completed: Boolean                   = false,
  endTime: Option[PersistableDateTime] = None,
  successCount: Int                    = 0,
  failureCount: Int                    = 0
) {
  def toDomain: ReplayState =
    ReplayState(
      startTime     = startTime.toInstant,
      endTime       = endTime.map(_.toInstant),
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
      startTime     = PersistableDateTime(startTime),
      endTime       = endTime.map(PersistableDateTime(_)),
      completed     = completed,
      successCount  = successCount,
      failureCount  = failureCount,
      totalToReplay = totalToReplay
    )
  }
}
