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

import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, MockEisConnector}
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationMetadata, MessageType, ReplayError, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.reporting.{MockReportSender, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.{MetadataLookupError, MockEntryDeclarationRepo}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class SubmissionReplayServiceSpec
    extends UnitSpec
    with MockEntryDeclarationRepo
    with MockEisConnector
    with MockReportSender
    with ScalaFutures {
  val service = new SubmissionReplayService(mockEntryDeclarationRepo, mockEisConnector, mockReportSender)

  val submissionIds: Seq[String] = Seq("subId1")
  val metadata: EntryDeclarationMetadata =
    EntryDeclarationMetadata("submissionId", MessageType.IE315, "5", Instant.now, None)
  private def submissionSentToEISReport(messageType: MessageType, failure: Option[EISSendFailure]) =
    SubmissionSentToEIS(
      eori          = eori,
      correlationId = correlationId,
      submissionId  = submissionId,
      messageType,
      failure
    )

  "SubmissionReplayService" when {
    "replaying a submission" when {
      "successful" must {
        "increment success count" in {
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(Right(metadata))
          MockEisConnector.submitMetadata(metadata) returns Future.successful(None)
          //MockReportSender

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(1, 0))
        }
      }

      "Metadata retrieval unsuccessful" must {
        "increment failure count" in {
          MockEntryDeclarationRepo
            .lookupMetadata(submissionIds.head)
            //WLOG
            .returns(Future.successful(Left(MetadataLookupError.DataFormatError)))

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(0, 1))
        }
        "terminate and return Left" in {
          MockEntryDeclarationRepo
            .lookupMetadata(submissionIds.head)
            .returns(Future.failed(new Exception)) //change exception

          fail

          service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.MetadataRetrievalError)
        }
      }

      "Sending to EIS unsuccessful" must {
        "increment failure count" in { //Which EISSendFailures
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(Right(metadata))
          MockEisConnector.submitMetadata(metadata) returns Future.successful(Some(EISSendFailure.ErrorResponse(400)))
          //MockReportSender
          fail
          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(0, 1))

        }
        "terminate and return Left" in { //Which EISSendFailures
          fail
          service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.EISSubmitError)
        }
      }

      "Sending the event unsuccessful" must {
        "terminate and return Left" in {
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(Right(metadata))
          MockEisConnector.submitMetadata(metadata) returns Future.successful(None)
          //MockReportSender
          fail
          service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.EISEventError)
        }
      }
    }
  }
}
