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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState.{Closed, Open}
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerStatus
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo

import scala.concurrent.Future

@Singleton
class CircuitBreakerService @Inject()(repo: CircuitBreakerRepo) {
  def resetCircuitBreaker: Future[Unit]                     = repo.resetToDefault
  def openCircuitBreaker: Future[Unit]                      = repo.setCircuitBreakerState(Open)
  def closeCircuitBreaker: Future[Unit]                     = repo.setCircuitBreakerState(Closed)
  def getCircuitBreakerStatus: Future[CircuitBreakerStatus] = repo.getCircuitBreakerStatus
}
