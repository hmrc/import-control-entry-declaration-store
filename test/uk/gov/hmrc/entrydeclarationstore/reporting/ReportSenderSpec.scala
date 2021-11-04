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

package uk.gov.hmrc.entrydeclarationstore.reporting

import com.kenshoo.play.metrics.Metrics
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsObject
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.entrydeclarationstore.reporting.audit.{AuditEvent, MockAuditHandler}
import uk.gov.hmrc.entrydeclarationstore.reporting.events.{Event, EventCode, MockEventConnector}
import uk.gov.hmrc.entrydeclarationstore.utils.MockMetrics
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Duration, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class ReportSenderSpec extends WordSpec with MockAuditHandler with MockEventConnector with ScalaFutures {

  // To show that it uses the specified clock for generating events...
  val clock: Clock = Clock.offset(Clock.fixed(Instant.now, ZoneOffset.UTC), Duration.ofSeconds(30))
  val now: Instant = Instant.now(clock)

  //WLOG
  val event: Event           = Event(EventCode.ENS_TO_EIS, now, "subId", "eori", "corrId", MessageType.IE313, None)
  val auditEvent: AuditEvent = AuditEvent("type", "trans", JsObject.empty)

  implicit val hc: HeaderCarrier  = HeaderCarrier()
  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  val mockedMetrics: Metrics = new MockMetrics

  val reportSender = new ReportSender(mockAuditHandler, mockEventConnector, clock, mockedMetrics)

  "ReportSender" must {
    object Report

    implicit val sources: EventSources[Report.type] = new EventSources[Report.type] {
      override def eventFor(timestamp: Instant, report: Report.type): Option[Event] = Some(event)
      override def auditEventFor(report: Report.type): Option[AuditEvent]           = Some(auditEvent)
    }

    "audit and send an event to the event microservice" in {
      MockAuditHandler.audit(auditEvent) returns Future.successful(())
      MockEventConnector.sendEvent(event) returns Future.successful(())

      reportSender.sendReport(Report).futureValue shouldBe ((): Unit)
    }

    "send event to the event microservice if the audit fails" in {
      MockAuditHandler.audit(auditEvent) returns Future.failed(new RuntimeException with NoStackTrace)
      MockEventConnector.sendEvent(event) returns Future.successful(())

      reportSender.sendReport(Report).futureValue shouldBe ((): Unit)
    }

    "audit if the event microservice send fails" in {
      val exception = new RuntimeException with NoStackTrace
      MockAuditHandler.audit(auditEvent) returns Future.successful(())
      MockEventConnector.sendEvent(event) returns Future.failed(exception)

      reportSender.sendReport(Report).failed.futureValue shouldBe exception
    }

    "only audit if no event microservice send required" in {

      object Report

      implicit val sources: EventSources[Report.type] = new EventSources[Report.type] {
        override def eventFor(timestamp: Instant, report: Report.type): Option[Event] = None
        override def auditEventFor(report: Report.type): Option[AuditEvent]           = Some(auditEvent)
      }

      MockAuditHandler.audit(auditEvent) returns Future.successful(())

      reportSender.sendReport(Report).futureValue shouldBe ((): Unit)
    }

    "only send event to the event microservice if no audit required" in {

      object Report

      implicit val sources: EventSources[Report.type] = new EventSources[Report.type] {
        override def eventFor(timestamp: Instant, report: Report.type): Option[Event] = Some(event)
        override def auditEventFor(report: Report.type): Option[AuditEvent]           = None
      }

      MockEventConnector.sendEvent(event) returns Future.successful(())

      reportSender.sendReport(Report).futureValue shouldBe ((): Unit)
    }
  }

}
