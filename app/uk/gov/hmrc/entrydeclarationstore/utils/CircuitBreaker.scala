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

package uk.gov.hmrc.entrydeclarationstore.utils

import akka.actor.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Circuit breaker that uses external shared open/closed state.
  *
  * Opens automatically after a number of successive failures
  * (or when the state is updated externally by another microservice instance)
  * but which must be manually closed by updating the external state.
  *
  * @param scheduler Reference to Akka scheduler
  * @param maxFailures Maximum number of failures before opening the circuit
  * @param callTimeout time after which to consider a call a failure
  */
class CircuitBreaker(scheduler: Scheduler, maxFailures: Int, callTimeout: FiniteDuration) {

  def withCircuitBreaker[T](body: => Future[T], defineFailureFn: Try[T] => Boolean): Future[T] = ???
}
