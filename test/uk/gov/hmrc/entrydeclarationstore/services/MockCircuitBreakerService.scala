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

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerStatus

import scala.concurrent.Future

trait MockCircuitBreakerService extends MockFactory {
  val mockCircuitBreakerService: CircuitBreakerService = mock[CircuitBreakerService]

  object MockCircuitBreakerService {
    def resetCircuitBreaker: CallHandler[Future[Unit]] =
      mockCircuitBreakerService.resetCircuitBreaker _ expects ()

    def openCircuitBreaker: CallHandler[Future[Unit]] =
      mockCircuitBreakerService.openCircuitBreaker _ expects ()

    def closeCircuitBreaker: CallHandler[Future[Unit]] =
      mockCircuitBreakerService.closeCircuitBreaker _ expects ()

    def getCircuitBreakerStatus: CallHandler[Future[CircuitBreakerStatus]] =
      mockCircuitBreakerService.getCircuitBreakerStatus _ expects ()
  }

}
