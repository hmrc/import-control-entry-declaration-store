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

sealed trait AutoReplayStatus

object AutoReplayStatus {

  case object On extends AutoReplayStatus
  case object Off extends AutoReplayStatus

  implicit val reads: Reads[AutoReplayStatus] = (JsPath \ "autoReplay").read[Boolean].map {
    case true  => On
    case false => Off
  }

  implicit val writes: Writes[AutoReplayStatus] = {
    case On => Json.obj("autoReplay" -> true)
    case Off => Json.obj("autoReplay" -> false)
  }

  implicit val format: Format[AutoReplayStatus] = Format(reads, writes)
}
