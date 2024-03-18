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

package uk.gov.hmrc.entrydeclarationstore.autoreplay

import com.github.pjfanning.pekko.scheduler.mock.VirtualTime
import org.apache.pekko.actor.{ActorSystem, Scheduler}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.repositories.LockRepositoryProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AutoReplaySchedulerISpec
    extends TestKit(ActorSystem("TrafficSwitchStateActorSpec"))
    with AnyWordSpecLike
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting
    with Eventually {

  val runInterval: FiniteDuration  = 1.minute
  val lockDuration: FiniteDuration = 1.second

  //implicit val ec: ExecutionContext = global
  private case object AutoReplay
  private val autoReplayProbe = TestProbe()

  val virtualTime                                    = new VirtualTime
  val lockRepositoryProvider: LockRepositoryProvider = inject[LockRepositoryProvider]

  override def beforeAll(): Unit =
    await(lockRepositoryProvider.removeAll())

  private def newAutoReplayer: AutoReplayer =  new AutoReplayer{
    def replay(replaySequenceCount: Int)(implicit ec: ExecutionContext): Future[Boolean] = {
      autoReplayProbe.ref ! AutoReplay
      Future.successful(false)
    }
  }

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .overrides(bind[AutoReplayer].toInstance(newAutoReplayer))
    .overrides(bind[Scheduler].toInstance(virtualTime.scheduler))
    .configure("auto-replay.runInterval" -> s"${runInterval.toMillis} millis")
    .configure("auto-replay.lockDuration" -> s"${lockDuration.toMillis} millis")
    .build()

  "AutoReplayScheduler" must {
    "repeatedly call replay (even though lock held)" in {
      virtualTime.advance(runInterval)
      autoReplayProbe.expectMsg(AutoReplay)

      virtualTime.advance(runInterval)
      autoReplayProbe.expectMsg(AutoReplay)
    }

    "not call more frequently than the run interval" in {
      virtualTime.advance(runInterval)
      autoReplayProbe.expectMsg(AutoReplay)

      autoReplayProbe.expectNoMessage(300.millis)
    }

    "prevent other instances from running until lock released" in {
      val virtualTime2 = new VirtualTime

      new AutoReplayScheduler(virtualTime2.scheduler, newAutoReplayer, lockRepositoryProvider, inject[AppConfig])

      virtualTime.advance(runInterval)
      autoReplayProbe.expectMsg(AutoReplay)

      // Lock will be held by main replayer - so ...
      virtualTime2.advance(runInterval)
      autoReplayProbe.expectNoMessage(300.millis)

      eventually(timeout(Span(2 * lockDuration.toMillis, Milliseconds))) {
        virtualTime2.advance(runInterval)
        autoReplayProbe.expectMsg(30.millis, AutoReplay)
      }
    }
  }
}
