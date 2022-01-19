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
import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed trait ReplayInitializationResult

object ReplayInitializationResult {
  case class Started(replayId: String) extends ReplayInitializationResult
  case class AlreadyRunning(replayId: Option[String]) extends ReplayInitializationResult

  implicit val writes: OWrites[ReplayInitializationResult] = {

    val fieldMap: ReplayInitializationResult => (Option[String], Boolean) = {
      case Started(replayId)        => (Some(replayId), false)
      case AlreadyRunning(replayId) => (replayId, true)
    }

    ((__ \ "replayId").writeNullable[String] and
      (__ \ "alreadyStarted").write[Boolean])(fieldMap)
  }
}
