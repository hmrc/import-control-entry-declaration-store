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
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}

import scala.concurrent.Future

trait MockTrafficSwitchRepo extends AsyncTestSuite with AsyncMockFactory {
  val mockTrafficSwitchRepo: TrafficSwitchRepo = mock[TrafficSwitchRepo]

  object MockTrafficSwitchRepo {
    def setTrafficSwitchState(value: TrafficSwitchState): CallHandler[Future[Option[TrafficSwitchStatus]]] =
      mockTrafficSwitchRepo.setTrafficSwitchState _ expects value

    def getTrafficSwitchStatus: CallHandler[Future[TrafficSwitchStatus]] =
      (() => mockTrafficSwitchRepo.getTrafficSwitchStatus).expects()

    def resetToDefault: CallHandler[Future[Unit]] =
      (() => mockTrafficSwitchRepo.resetToDefault).expects()
  }
}
