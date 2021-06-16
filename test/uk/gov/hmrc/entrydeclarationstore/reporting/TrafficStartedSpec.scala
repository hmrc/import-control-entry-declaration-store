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

import org.scalatest.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.Json
import org.scalatest.WordSpec

import java.time.{Duration, Instant}

class TrafficStartedSpec extends WordSpec {

  val now: Instant = Instant.now
  val timeTaken    = 100

  val duration: Duration             = Duration.ofMillis(timeTaken)
  val trafficStarted: TrafficStarted = TrafficStarted(duration)

  "TrafficStarted" must {
    "have the correct associated JSON event" in {
      val event = implicitly[EventSources[TrafficStarted]].eventFor(now, trafficStarted)

      event shouldBe None
    }

    "have the correct associated audit event" in {
      val event = implicitly[EventSources[TrafficStarted]].auditEventFor(trafficStarted).get

      event.auditType       shouldBe "TrafficStarted"
      event.transactionName shouldBe "Traffic Started"
      event.detail          shouldBe Json.obj("durationStopped" -> timeTaken)
    }
  }
}
