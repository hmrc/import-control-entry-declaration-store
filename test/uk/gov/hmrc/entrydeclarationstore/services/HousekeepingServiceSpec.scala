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

package uk.gov.hmrc.entrydeclarationstore.services

import com.codahale.metrics.MetricRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.{MockEntryDeclarationRepo, MockHousekeepingRepo}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.duration._

class HousekeepingServiceSpec
    extends AnyWordSpec
    with MockAppConfig
    with MockEntryDeclarationRepo
    with MockHousekeepingRepo
    with ScalaFutures {

  val time: Instant = Instant.now
  val clock: Clock  = Clock.fixed(time, ZoneOffset.UTC)

  val metrics: MetricRegistry = new MetricRegistry()
  val service =
    new HousekeepingService(mockEntryDeclarationRepo, mockHousekeepingRepo, clock, mockAppConfig, metrics)

  "HousekeepingService" when {
    "getting housekeeping status" must {
      "get using the repo" in {
        // WLOG
        val status = HousekeepingStatus(true)

        MockHousekeepingRepo.getHousekeepingStatus returns Future.successful(status)
        service.getHousekeepingStatus.futureValue shouldBe status
      }
    }

    "setting housekeeping status" must {
      "set using the repo" in {
        // WLOG
        val value = false

        MockHousekeepingRepo.enableHousekeeping(value) returns Future.unit
        service.enableHousekeeping(value).futureValue
      }
    }

    "setting a short ttl" must {
      "set using the repo" when {
        val success = true
        val newTtl  = 1.day
        "searching by submission" in {
          val submissionId = "submissionId"

          MockAppConfig.shortTtl returns newTtl
          MockEntryDeclarationRepo.setHousekeepingAt(submissionId, time.plusMillis(newTtl.toMillis))
            .returns(Future.successful(success))

          service.setShortTtl(submissionId).futureValue shouldBe success
        }
        "searching by eori and correlation Id" in {
          val eori          = "eori"
          val correlationId = "correlationId"

          MockAppConfig.shortTtl returns newTtl
          MockEntryDeclarationRepo
            .setHousekeepingAt(eori, correlationId, time.plusMillis(newTtl.toMillis))
            .returns(Future.successful(success))

          service.setShortTtl(eori, correlationId).futureValue shouldBe success
        }
      }
    }

    "performing housekeeping" must {
      "use the current date" in {
        MockHousekeepingRepo.getHousekeepingStatus returns Future.successful(HousekeepingStatus(true))
        val numDeleted = 123
        MockEntryDeclarationRepo.housekeep(time) returns Future.successful(numDeleted)

        service.housekeep().futureValue shouldBe true
      }

      "do nothing when housekeeping is off" in {
        MockHousekeepingRepo.getHousekeepingStatus returns Future.successful(HousekeepingStatus(false))

        service.housekeep().futureValue shouldBe false
      }
    }
  }
}
