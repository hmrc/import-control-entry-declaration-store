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

import play.api.libs.json.{Format, Json}

import java.time.Instant

case class ReplayState(
  replayId: String,
  startTime: Instant,
  totalToReplay: Int,
  trigger: ReplayTrigger,
  completed: Boolean = false,
  endTime: Option[Instant] = None,
  successCount: Int = 0,
  failureCount: Int = 0)

object ReplayState {
  object Implicits {
    implicit val replayStateFormat: Format[ReplayState] = Json.format[ReplayState]
  }

  object MongoImplicits {
    import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._

    implicit val mongoFormat: Format[ReplayState] = Json.format[ReplayState]
  }
}