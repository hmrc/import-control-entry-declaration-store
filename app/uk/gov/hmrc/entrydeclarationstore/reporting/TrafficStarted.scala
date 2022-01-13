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

package uk.gov.hmrc.entrydeclarationstore.reporting

import play.api.libs.json.Json
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationstore.reporting.events.Event

import java.time.{Duration, Instant}

case class TrafficStarted(durationStopped: Duration)

object TrafficStarted {
  implicit val eventSources: EventSources[TrafficStarted] = new EventSources[TrafficStarted] {
    override def eventFor(timestamp: Instant, report: TrafficStarted): Option[Event] = None

    override def auditEventFor(report: TrafficStarted): Option[AuditEvent] =
      Some(AuditEvent("TrafficStarted", "Traffic Started", Json.obj("durationStopped" -> report.durationStopped.toMillis)))
  }
}
