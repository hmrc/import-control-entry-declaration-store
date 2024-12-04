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

package uk.gov.hmrc.entrydeclarationstore.repositories

import org.scalamock.handlers.CallHandler
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.utils.TestHarness

import scala.concurrent.Future

trait MockHousekeepingRepo extends TestHarness {
  val mockHousekeepingRepo: HousekeepingRepo = mock[HousekeepingRepo]

  object MockHousekeepingRepo {
    def enableHousekeeping(value: Boolean): CallHandler[Future[Unit]] =
      mockHousekeepingRepo.enableHousekeeping _ expects value

    def getHousekeepingStatus: CallHandler[Future[HousekeepingStatus]] =
      (() => mockHousekeepingRepo.getHousekeepingStatus).expects()
  }
}
