/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{Clock, Instant, ZoneOffset}

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class HousekeepingServiceSpec extends UnitSpec with MockAppConfig with MockEntryDeclarationRepo with ScalaFutures {

  val time: Instant = Instant.now
  val clock: Clock  = Clock.fixed(time, ZoneOffset.UTC)

  val service = new HousekeepingService(mockEntryDeclarationRepo, clock, mockAppConfig)

  "HousekeepingService" when {
    "getting housekeeping status" must {
      "get using the repo" in {
        // WLOG
        val status = HousekeepingStatus.On

        MockEntryDeclarationRepo.getHousekeepingStatus returns status
        service.getHousekeepingStatus.futureValue shouldBe status
      }
    }

    "setting housekeeping status" must {
      "set using the repo" in {
        // WLOG
        val success = true
        val value   = false

        MockEntryDeclarationRepo.enableHousekeeping(value) returns success
        service.enableHousekeeping(value).futureValue shouldBe success
      }
    }

    "setting a short ttl" must {
      "set using the repo" when {
        val success = true
        val newTtl  = 1.day
        "searching by submission" in {
          val submissionId = "submissionId"

          MockAppConfig.shortTtl returns newTtl
          MockEntryDeclarationRepo.setHousekeepingAt(submissionId, time.plusMillis(newTtl.toMillis)).returns(success)

          service.setShortTtl(submissionId).futureValue shouldBe success
        }
        "searching by eori and correlation Id" in {
          val eori          = "eori"
          val correlationId = "correlationId"

          MockAppConfig.shortTtl returns newTtl
          MockEntryDeclarationRepo
            .setHousekeepingAt(eori, correlationId, time.plusMillis(newTtl.toMillis))
            .returns(success)

          service.setShortTtl(eori, correlationId).futureValue shouldBe success
        }
      }
    }
  }
}
