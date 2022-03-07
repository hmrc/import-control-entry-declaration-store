/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, Inside}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayTrigger, ReplayState}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ReplayStateRepoISpec
    extends AnyWordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting
    with Inside {

  lazy val repository: ReplayStateRepoImpl = inject[ReplayStateRepoImpl]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repository.removeAll)
  }
  val replayId = "replayId"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false")
    .build()

  val totalToReplay: Int       = 10
  val startTime: Instant       = Instant.now
  val replayState: ReplayState = ReplayState(ReplayTrigger.Manual, startTime, None, completed = false, 0, 0, totalToReplay)
  "ReplayStateRepo" when {
    "inserting a replay state" must {
      "work" in {
        await(repository.insert(replayId, ReplayTrigger.Manual, totalToReplay, startTime))
      }
      "fail if the replayId exists" in {
        intercept[Exception] {
          await(repository.insert(replayId, ReplayTrigger.Manual, totalToReplay, startTime))
        }
      }
      "create a replay of the correct format" in {
        await(repository.lookupState(replayId)) shouldBe Some(replayState)
      }
    }
    "looking up a replay that does not exist" must {
      "return None" in {
        await(repository.lookupState("unknownReplayId")) shouldBe None
      }
    }
    "updating a replay record" must {
      val replayIdToUpdate = "otherReplayId"
      "return true and increment correctly" in {
        val successesToAdd = 3
        val failuresToAdd  = 4
        await(repository.insert(replayIdToUpdate, ReplayTrigger.Manual, totalToReplay, startTime))
        await(repository.incrementCounts(replayIdToUpdate, successesToAdd, failuresToAdd)) shouldBe true
        await(repository.lookupState(replayIdToUpdate)) shouldBe
          Some(replayState.copy(successCount = successesToAdd, failureCount = failuresToAdd))
        await(repository.incrementCounts(replayIdToUpdate, successesToAdd, failuresToAdd)) shouldBe true
        await(repository.lookupState(replayIdToUpdate)) shouldBe Some(
          replayState
            .copy(successCount = successesToAdd * 2, failureCount = failuresToAdd * 2))

      }
      "return false if the replay does not exist" in {
        await(repository.incrementCounts("unknownReplayId", 1, 2)) shouldBe false
      }
    }
    "setting a replay to completed" must {
      val endTime = Instant.now
      "return update the replay and return true if the replay exists" in {
        await(repository.setCompleted(replayId, endTime)) shouldBe true
        await(repository.lookupState(replayId)) shouldBe Some(
          replayState.copy(endTime = Some(endTime), completed = true))
      }
      "return false if replay does not exist" in {
        await(repository.setCompleted("unknownReplayId", endTime)) shouldBe false
      }
    }

    "putting the state" when {
      val otherReplayId = "someReplayId1"
      val t1            = Instant.now

      "no state for a replayId exists" must {
        "insert" in {
          val replayState = ReplayState(ReplayTrigger.Manual, t1, None, completed = false, 0, 0, 0)

          await(repository.setState(otherReplayId, replayState))
          await(repository.lookupState(otherReplayId)) shouldBe Some(replayState)
        }
      }
      "state for a replayId already exists" must {
        "update" in {
          val t2 = t1.plusSeconds(1)

          // Update every field...
          val updatedReplayState = ReplayState(ReplayTrigger.Manual, t2, Some(t2), completed = true, 123, 654, 777)

          await(repository.setState(otherReplayId, updatedReplayState))
          await(repository.lookupState(otherReplayId)) shouldBe Some(updatedReplayState)
        }
      }
    }

    "looking up the latest replay" when {
      "no replay states exist" must {
        "return None" in {
          await(repository.removeAll)
          await(repository.lookupIdOfLatest) shouldBe None
        }
      }

      "an incomplete replay state exists" must {
        "return it" in {
          await(repository.removeAll)
          await(repository.insert(replayId, ReplayTrigger.Manual, totalToReplay, startTime))
          await(repository.lookupIdOfLatest) shouldBe Some(replayId)
        }
      }

      "multiple replay states exist" must {
        "return the one with the latest start date" in {
          await(repository.removeAll)
          val num = 10

          val idsAndStarts = Random.shuffle((1 to num).map(i => (s"replayId-$i", startTime.plusSeconds(i))))
          val idOfLatest   = idsAndStarts.maxBy(_._2)._1

          await(Future.traverse(idsAndStarts) { case (id, start) => repository.insert(id, ReplayTrigger.Manual, totalToReplay, start) })

          await(repository.lookupIdOfLatest) shouldBe Some(idOfLatest)
        }
      }

      "return any if multiple have same start date" in {
        await(repository.removeAll)

        val replayId1 = "replayId-1"
        val replayId2 = "replayId-2"

        await(repository.insert(replayId1, ReplayTrigger.Manual, totalToReplay, startTime))
        await(repository.insert(replayId2, ReplayTrigger.Manual, totalToReplay, startTime))

        await(repository.lookupIdOfLatest) should (be(Some(replayId1)) or be(Some(replayId2)))
      }
    }
  }
}
