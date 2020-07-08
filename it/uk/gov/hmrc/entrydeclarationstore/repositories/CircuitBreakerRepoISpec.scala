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

import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.models.{CircuitBreakerState, CircuitBreakerStatus}

import scala.concurrent.ExecutionContext.Implicits.global

class CircuitBreakerRepoISpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with Eventually
    with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false")
    .build()

  lazy val repository: CircuitBreakerRepoImpl = inject[CircuitBreakerRepoImpl]

  "CircuitBreeakerRepo" when {
    "database is empty" when {

      trait Scenario {
        await(repository.removeAll())
      }

      "get" must {
        "return state as closed" in new Scenario {
          await(repository.getCircuitBreakerStatus) shouldBe CircuitBreakerStatus(
            CircuitBreakerState.Closed,
            None,
            None)
        }
      }

      "set closed" must {
        "do nothing" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Closed)) shouldBe ()

          await(repository.getCircuitBreakerStatus) shouldBe CircuitBreakerStatus(
            CircuitBreakerState.Closed,
            None,
            None)
        }
      }

      "set open" must {
        "set open and update the last open date" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Open)) shouldBe ()

          val status: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
          status.circuitBreakerState shouldBe CircuitBreakerState.Open
          status.lastOpened          should not be empty
          status.lastClosed          shouldBe empty
        }
      }
    }

    "datebase state is explicitly open" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.setCircuitBreaker(CircuitBreakerState.Open))

        val initialStatus: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
      }

      "get" must {
        "return state as open" in new Scenario {
          initialStatus.circuitBreakerState shouldBe CircuitBreakerState.Open
          initialStatus.lastClosed          shouldBe empty
          initialStatus.lastOpened          should not be empty
        }
      }

      "set open" must {
        "do nothing" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Open))

          await(repository.getCircuitBreakerStatus) shouldBe initialStatus
        }
      }

      "set closed" must {
        "set closed and update the last closed date" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Closed))

          val status: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
          status.circuitBreakerState shouldBe CircuitBreakerState.Closed
          status.lastClosed          should not be empty
          status.lastOpened          shouldBe initialStatus.lastOpened
        }
      }
    }

    "datebase state is closed" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.setCircuitBreaker(CircuitBreakerState.Open))
        await(repository.setCircuitBreaker(CircuitBreakerState.Closed))

        val initialStatus: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
      }

      "get" must {
        "return state as closed" in new Scenario {
          initialStatus.circuitBreakerState shouldBe CircuitBreakerState.Closed
          initialStatus.lastClosed          should not be empty
          initialStatus.lastOpened          should not be empty
        }
      }

      "set closed" must {
        "do nothing" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Closed))

          await(repository.getCircuitBreakerStatus) shouldBe initialStatus
        }
      }

      "set open" must {
        "set open and update the last open date" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Open))

          val status: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
          status.circuitBreakerState shouldBe CircuitBreakerState.Open
          status.lastClosed          shouldBe initialStatus.lastClosed
          status.lastOpened          shouldBe >(initialStatus.lastOpened)
        }
      }
    }

    // Case where both lastClosed and lastOpened dates are set initially
    "circuit breaker is opened after previous close" must {
      trait Scenario {
        await(repository.removeAll())
        await(repository.setCircuitBreaker(CircuitBreakerState.Open))
        await(repository.setCircuitBreaker(CircuitBreakerState.Closed))
        await(repository.setCircuitBreaker(CircuitBreakerState.Open))

        val initialStatus: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
      }

      "set open" must {
        "do nothing" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Open))

          await(repository.getCircuitBreakerStatus) shouldBe initialStatus
        }
      }

      "set closed" must {
        "set closed and update the last closed date" in new Scenario {
          await(repository.setCircuitBreaker(CircuitBreakerState.Closed))

          val status: CircuitBreakerStatus = await(repository.getCircuitBreakerStatus)
          status.circuitBreakerState shouldBe CircuitBreakerState.Closed
          status.lastClosed          shouldBe >(initialStatus.lastClosed)
          status.lastOpened          shouldBe initialStatus.lastOpened
        }
      }
    }
  }
}
