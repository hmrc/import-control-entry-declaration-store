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

import org.scalamock.handlers.CallHandler
import uk.gov.hmrc.entrydeclarationstore.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationstore.utils.TestHarness

import scala.concurrent.Future

trait MockHousekeepingService extends TestHarness {
  val mockHousekeepingService: HousekeepingService = mock[HousekeepingService]

  object MockHousekeepingService {
    def enableHousekeeping(value: Boolean): CallHandler[Future[Unit]] =
      mockHousekeepingService.enableHousekeeping _ expects value

    def getHousekeepingStatus: CallHandler[Future[HousekeepingStatus]] =
      (() => mockHousekeepingService.getHousekeepingStatus).expects()

    def setShortTtl(submissionId: String): CallHandler[Future[Boolean]] =
      (mockHousekeepingService.setShortTtl(_: String)) expects submissionId

    def setShortTtl(eori: String, correlationId: String): CallHandler[Future[Boolean]] =
      (mockHousekeepingService.setShortTtl(_: String, _: String)).expects(eori, correlationId)

    def housekeep(): CallHandler[Future[Boolean]] =
      (() => mockHousekeepingService.housekeep()).expects()
  }

}
