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

package uk.gov.hmrc.entrydeclarationstore.reporting.events

import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.entrydeclarationstore.models.MessageType

import java.time.Instant

case class Event(
  eventCode: EventCode,
  eventTimestamp: Instant,
  submissionId: String,
  eori: String,
  correlationId: String,
  messageType: MessageType,
  detail: Option[JsObject]
)

object Event {
  implicit val writes: Writes[Event] = Json.writes[Event]
}
