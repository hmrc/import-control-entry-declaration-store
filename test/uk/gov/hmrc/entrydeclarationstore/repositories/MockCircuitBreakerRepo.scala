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

package uk.gov.hmrc.entrydeclarationstore.repositories

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState

import scala.concurrent.Future

trait MockCircuitBreakerRepo extends MockFactory {
  val mockCircuitBreakerRepo: CircuitBreakerRepo = mock[CircuitBreakerRepo]

  object MockCircuitBreakerRepo {
    def getCircuitBreakerState: CallHandler[Future[CircuitBreakerState]] =
      (mockCircuitBreakerRepo.getCircuitBreakerState _).expects()

    def setCircuitBreakerState(circuitBreakerState: CircuitBreakerState): CallHandler[Future[Unit]] =
      (mockCircuitBreakerRepo.setCircuitBreakerState _).expects(circuitBreakerState)
  }
}
