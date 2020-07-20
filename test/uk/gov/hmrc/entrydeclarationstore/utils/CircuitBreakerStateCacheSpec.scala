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

import org.scalatest.concurrent.ScalaFutures
import reactivemongo.core.errors.ConnectionException
import uk.gov.hmrc.entrydeclarationstore.models.CircuitBreakerState
import uk.gov.hmrc.entrydeclarationstore.repositories.MockCircuitBreakerRepo
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

class CircuitBreakerStateCacheSpec extends UnitSpec with MockCircuitBreakerRepo with ScalaFutures {

  class Test {
    val ttl: FiniteDuration             = 300.millis
    val cache: CircuitBreakerStateCache = new CircuitBreakerStateCache(mockCircuitBreakerRepo, ttl)
  }

  "CircuitBreakerStateCache" when {
    "state is no in cache" must {
      "get state from database" in new Test {
        MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open)

        cache.getCircuitBreakerState.futureValue shouldBe CircuitBreakerState.Open
      }

      "get state once despite multiple callers while getting" in new Test {
        val promise: Promise[CircuitBreakerState] = Promise[CircuitBreakerState]

        MockCircuitBreakerRepo.getCircuitBreakerState returns promise.future

        val call1: Future[CircuitBreakerState] = cache.getCircuitBreakerState
        val call2: Future[CircuitBreakerState] = cache.getCircuitBreakerState

        promise.success(CircuitBreakerState.Open)

        call1.futureValue shouldBe CircuitBreakerState.Open
        call2.futureValue shouldBe CircuitBreakerState.Open
      }
    }

    "database cannot be accessed" must {
      val e = new ConnectionException("some message") with NoStackTrace

      "not cache the failure" in new Test {
        MockCircuitBreakerRepo.getCircuitBreakerState returns Future.failed(e) twice ()

        cache.getCircuitBreakerState.failed.futureValue shouldBe e
        cache.getCircuitBreakerState.failed.futureValue shouldBe e
      }

      "not cache the failure despite multiple callers while getting" in new Test {
        val promise: Promise[CircuitBreakerState] = Promise[CircuitBreakerState]

        MockCircuitBreakerRepo.getCircuitBreakerState returns promise.future twice ()

        val call1: Future[CircuitBreakerState] = cache.getCircuitBreakerState
        val call2: Future[CircuitBreakerState] = cache.getCircuitBreakerState

        promise.failure(e)

        call1.failed.futureValue shouldBe e
        call2.failed.futureValue shouldBe e
      }
    }

    "state is in cache" must {
      "use the cached value" in new Test {
        MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open) once ()

        cache.getCircuitBreakerState.futureValue shouldBe CircuitBreakerState.Open
        cache.getCircuitBreakerState.futureValue shouldBe CircuitBreakerState.Open
      }

      "expire the value a time" in new Test {
        MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Open) once ()
        MockCircuitBreakerRepo.getCircuitBreakerState returns Future.successful(CircuitBreakerState.Closed) once ()

        cache.getCircuitBreakerState.futureValue shouldBe CircuitBreakerState.Open
        Thread.sleep(ttl.toMillis)
        cache.getCircuitBreakerState.futureValue shouldBe CircuitBreakerState.Closed
      }
    }
  }
}
