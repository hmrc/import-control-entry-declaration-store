/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.housekeeping

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.{TestKit, TestProbe}
import com.miguno.akka.testing.VirtualTime
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.repositories.LockRepositoryProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class HousekeepingSchedulerISpec
    extends TestKit(ActorSystem("TrafficSwitchStateActorSpec"))
    with WordSpecLike
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting
    with Eventually {

  val runInterval: FiniteDuration  = 1.minute
  val lockDuration: FiniteDuration = 1.second

  private case object HousekeepCalled
  private val housekeeperProbe = TestProbe()

  val virtualTime                                    = new VirtualTime
  val lockRepositoryProvider: LockRepositoryProvider = inject[LockRepositoryProvider]

  override def beforeAll(): Unit =
    await(lockRepositoryProvider.lockRepository.removeAll())

  private def newHousekeeper: Housekeeper = () => {
    housekeeperProbe.ref ! HousekeepCalled
    Future.successful(true)
  }

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .overrides(bind[Housekeeper].toInstance(newHousekeeper))
    .overrides(bind[Scheduler].toInstance(virtualTime.scheduler))
    .configure("metrics.enabled" -> "false")
    .configure("mongodb.housekeepingRunInterval" -> s"${runInterval.toMillis} millis")
    .configure("mongodb.housekeepingLockDuration" -> s"${lockDuration.toMillis} millis")
    .build()

  "HousekeepingScheduler" must {
    "repeatedly call housekeep (even though lock held)" in {
      virtualTime.advance(runInterval)
      housekeeperProbe.expectMsg(HousekeepCalled)

      virtualTime.advance(runInterval)
      housekeeperProbe.expectMsg(HousekeepCalled)
    }

    "not call more frequently than the run interval" in {
      virtualTime.advance(runInterval)
      housekeeperProbe.expectMsg(HousekeepCalled)

      housekeeperProbe.expectNoMessage(300.millis)
    }

    "prevent other instances from running until lock released" in {
      val virtualTime2 = new VirtualTime

      new HousekeepingScheduler(virtualTime2.scheduler, newHousekeeper, lockRepositoryProvider, inject[AppConfig])

      virtualTime.advance(runInterval)
      housekeeperProbe.expectMsg(HousekeepCalled)

      // Lock will be held by main housekeeper - so ...
      virtualTime2.advance(runInterval)
      housekeeperProbe.expectNoMessage(300.millis)

      eventually(timeout(Span(2 * lockDuration.toMillis, Milliseconds))) {
        virtualTime2.advance(runInterval)
        housekeeperProbe.expectMsg(30.millis, HousekeepCalled)
      }
    }
  }
}
