/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entrydeclarationstore.services

import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationstore.models.ReplayState
import uk.gov.hmrc.entrydeclarationstore.repositories.MockReplayStateRepo
import uk.gov.hmrc.play.test.UnitSpec

class ReplayStateRetrievalServiceSpec extends UnitSpec with MockReplayStateRepo with ScalaFutures {

  val service  = new ReplayStateRetrievalService(mockReplayStateRepo)
  val replayId = "replayId"

  "SubmissionStateRetrievalService" when {
    "submission exists for a submissionId" must {
      "return it" in {
        val state = ReplayState(Instant.now, None, completed = false, 0, 1, 2)

        MockReplayStateRepo.lookupState(replayId) returns Some(state)

        service.retrieveReplayState(replayId).futureValue shouldBe Some(state)
      }
    }

    "no submission exists for a submissionId" must {
      "return None" in {
        MockReplayStateRepo.lookupState(replayId) returns None

        service.retrieveReplayState(replayId).futureValue shouldBe None
      }
    }
  }
}
