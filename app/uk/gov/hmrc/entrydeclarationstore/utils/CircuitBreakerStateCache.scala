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

import javax.inject.{Inject, Singleton}
import scalacache.caffeine._
import scalacache.memoization._
import scalacache.modes.scalaFuture._
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.CircuitBreakerRepo

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CircuitBreakerStateCache @Inject()(circuitBreakerRepo: CircuitBreakerRepo, ttl: FiniteDuration)(
  implicit ec: ExecutionContext) {

  implicit val cache: CaffeineCache[CircuitBreakerState] = CaffeineCache[CircuitBreakerState]

  def getCircuitBreakerState: Future[CircuitBreakerState] =
    memoizeF(Some(ttl))(circuitBreakerRepo.getCircuitBreakerState)

}
