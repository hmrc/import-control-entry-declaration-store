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

import java.io.IOException
import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status.BAD_REQUEST
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, MockEisConnector}
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationMetadata, MessageType, ReplayError, ReplayMetadata, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.reporting.{MockReportSender, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.{MetadataLookupError, MockEntryDeclarationRepo}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionReplayServiceSpec
    extends UnitSpec
    with MockEntryDeclarationRepo
    with MockEisConnector
    with MockReportSender
    with ScalaFutures {

  val service = new SubmissionReplayService(mockEntryDeclarationRepo, mockEisConnector, mockReportSender)

  val eori          = "eori"
  val correlationId = "correlationId"
  val now: Instant  = Instant.now

  def metadata(submissionId: String): EntryDeclarationMetadata =
    EntryDeclarationMetadata(submissionId, MessageType.IE315, "5", now, None)

  def replayMetadata(submissionId: String): ReplayMetadata = ReplayMetadata(eori, correlationId, metadata(submissionId))

  private def submissionSentToEISReport(submissionId: String, failure: Option[EISSendFailure]) =
    SubmissionSentToEIS(
      eori          = eori,
      correlationId = correlationId,
      submissionId,
      MessageType.IE315,
      failure
    )

  "SubmissionReplayService" when {
    "replaying a submission" when {
      "successful" must {
        "increment success count" in {
          val submissionIds = Seq("subId1")

          val report = submissionSentToEISReport(submissionIds.head, None)
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(
            Right(replayMetadata("subId1")))
          MockEisConnector.submitMetadata(metadata("subId1")) returns Future.successful(None)
          MockReportSender.sendReport(report) returns Future.successful((): Unit)

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(1, 0))
        }
        "increment success counts" in {
          val submissionIds = Seq("subId1", "subId2")
          submissionIds.foreach { submissionId =>
            val report = submissionSentToEISReport(submissionId, None)
            MockEntryDeclarationRepo
              .lookupMetadata(submissionId)
              .returns(Future.successful(Right(replayMetadata(submissionId))))
            MockEisConnector.submitMetadata(metadata(submissionId)) returns Future.successful(None)
            MockReportSender.sendReport(report) returns Future.successful((): Unit)
          }

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(2, 0))
        }
      }

      "Metadata retrieval unsuccessful" must {
        "increment failure count" in {
          val submissionIds = Seq("subId1")

          MockEntryDeclarationRepo
            .lookupMetadata(submissionIds.head)
            //WLOG
            .returns(Future.successful(Left(MetadataLookupError.DataFormatError)))

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(0, 1))
        }
        "terminate and return Left" in {
          val submissionIds = Seq("subId1", "subId2")

          MockEntryDeclarationRepo
            .lookupMetadata(submissionIds.head)
            .returns(Future.failed(GenericDatabaseException("abc", None)))

          service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.MetadataRetrievalError)
        }
      }

      "Sending to EIS unsuccessful" must {
        "increment failure count" in {
          val submissionIds = Seq("subId1", "subId2")

          MockEntryDeclarationRepo.lookupMetadata("subId1").returns(Future.successful(Right(replayMetadata("subId1"))))
          val errorResponse = Some(EISSendFailure.ErrorResponse(BAD_REQUEST))
          val errorReport   = submissionSentToEISReport(submissionIds.head, errorResponse)
          MockEisConnector
            .submitMetadata(metadata("subId1"))
            .returns(Future.successful(errorResponse))
          MockReportSender.sendReport(errorReport) returns Future.successful((): Unit)

          val successReport = submissionSentToEISReport("subId2", None)
          MockEntryDeclarationRepo.lookupMetadata("subId2").returns(Future.successful(Right(replayMetadata("subId2"))))
          MockEisConnector.submitMetadata(metadata("subId2")) returns Future.successful(None)
          MockReportSender.sendReport(successReport) returns Future.successful((): Unit)

          service.replaySubmission(submissionIds).futureValue shouldBe Right(ReplayResult(1, 1))

        }
        "terminate and return Left" when { //make into method to call all other EISSendFailureCases and example Error Codes
          s"EISSendFailure is ${EISSendFailure.CircuitBreakerOpen}" in {
            val submissionIds = Seq("subId1", "subId2")

            MockEntryDeclarationRepo
              .lookupMetadata("subId1")
              .returns(Future.successful(Right(replayMetadata("subId1"))))
            val errorResponse = Some(EISSendFailure.CircuitBreakerOpen)
            val errorReport   = submissionSentToEISReport(submissionIds.head, errorResponse)
            MockEisConnector
              .submitMetadata(metadata("subId1"))
              .returns(Future.successful(errorResponse))
            MockReportSender.sendReport(errorReport) returns Future.successful((): Unit)

            service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.EISSubmitError)
          }
        }
      }

      "Sending the event unsuccessful" must {
        "terminate and return Left" in {
          val submissionIds = Seq("subId1", "subId2")

          val report = submissionSentToEISReport("subId1", None)
          MockEntryDeclarationRepo.lookupMetadata("subId1") returns Future.successful(Right(replayMetadata("subId1")))
          MockEisConnector.submitMetadata(metadata("subId1")) returns Future.successful(None)
          MockReportSender.sendReport(report) returns Future.failed(new IOException)

          service.replaySubmission(submissionIds).futureValue shouldBe Left(ReplayError.EISEventError)
        }
      }
    }
  }
}