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

package uk.gov.hmrc.entrydeclarationstore.services

import java.io.IOException
import java.time.{Clock, Instant, ZoneOffset}

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status._
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, MockEisConnector}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.entrydeclarationstore.reporting.{MockReportSender, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.{MetadataLookupError, MockEntryDeclarationRepo}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionReplayServiceSpec
    extends AnyWordSpec
    with MockEntryDeclarationRepo
    with MockEisConnector
    with MockReportSender
    with ScalaFutures {

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  val service = new SubmissionReplayService(mockEntryDeclarationRepo, mockEisConnector, mockReportSender, clock)

  val eori          = "eori"
  val correlationId = "correlationId"
  val subId1        = "subId1"
  val subId2        = "subId2"

  implicit val hc: HeaderCarrier = HeaderCarrier()

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
    "replaying submissions" when {
      "successful" must {
        "increment success count" in {
          val submissionIds = Seq(subId1)

          val report = submissionSentToEISReport(submissionIds.head, None)
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(
            Right(replayMetadata(subId1)))
          MockEisConnector.submitMetadata(metadata(subId1), bypassTrafficSwitch = true) returns Future.successful(None)
          MockReportSender.sendReport(report) returns Future.successful((): Unit)
          MockEntryDeclarationRepo.setEisSubmissionSuccess(subId1, Instant.now(clock)) returns Future.successful(true)

          service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(1, 0))
        }
        "increment success counts" in {
          val submissionIds = Seq(subId1, subId2)
          submissionIds.foreach { submissionId =>
            val report = submissionSentToEISReport(submissionId, None)
            MockEntryDeclarationRepo
              .lookupMetadata(submissionId)
              .returns(Future.successful(Right(replayMetadata(submissionId))))
            MockEisConnector.submitMetadata(metadata(submissionId), bypassTrafficSwitch = true) returns Future
              .successful(None)
            MockReportSender.sendReport(report) returns Future.successful((): Unit)
            MockEntryDeclarationRepo.setEisSubmissionSuccess(submissionId, Instant.now(clock)) returns Future
              .successful(true)
          }

          service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(2, 0))
        }
        "ignore failures to update the EIS submission time" in {
          val submissionIds = Seq(subId1)

          val report = submissionSentToEISReport(submissionIds.head, None)
          MockEntryDeclarationRepo.lookupMetadata(submissionIds.head) returns Future.successful(
            Right(replayMetadata(subId1)))
          MockEisConnector.submitMetadata(metadata(subId1), bypassTrafficSwitch = true) returns Future.successful(None)
          MockReportSender.sendReport(report) returns Future.successful((): Unit)
          MockEntryDeclarationRepo.setEisSubmissionSuccess(subId1, Instant.now(clock)) returns Future.successful(false)

          service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(1, 0))
        }
      }

      "Metadata retrieval unsuccessful" must {
        "increment failure count" when {
          "MetadataLookupError is DataFormatError" in {
            val submissionIds = Seq(subId1)

            MockEntryDeclarationRepo
              .lookupMetadata(submissionIds.head)
              //WLOG
              .returns(Future.successful(Left(MetadataLookupError.DataFormatError)))

            service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(0, 1))
          }
          "MetadataLookupError is MetadataNotFound" in {
            val submissionIds = Seq(subId1)

            MockEntryDeclarationRepo
              .lookupMetadata(submissionIds.head)
              //WLOG
              .returns(Future.successful(Left(MetadataLookupError.MetadataNotFound)))

            service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(0, 1))
          }
        }
        "terminate and return Left" when {
          "an exception is thrown" in {
            val submissionIds = Seq(subId1, subId2)

            MockEntryDeclarationRepo
              .lookupMetadata(submissionIds.head)
              .returns(Future.failed(new Exception("abc")))

            service.replaySubmissions(submissionIds).futureValue shouldBe Left(Abort(BatchReplayError.MetadataRetrievalError,Counts(0,1)))
          }
        }
      }

      "Sending to EIS unsuccessful" must {
        "increment failure count" when {
          "EISSendFailure is ErrorResponse(400)" in {
            val submissionIds = Seq(subId1, subId2)

            MockEntryDeclarationRepo
              .lookupMetadata(subId1)
              .returns(Future.successful(Right(replayMetadata(subId1))))
            val errorResponse = Some(EISSendFailure.ErrorResponse(BAD_REQUEST))
            val errorReport   = submissionSentToEISReport(submissionIds.head, errorResponse)
            MockEisConnector
              .submitMetadata(metadata(subId1), bypassTrafficSwitch = true)
              .returns(Future.successful(errorResponse))
            MockReportSender.sendReport(errorReport) returns Future.successful((): Unit)
            val successReport = submissionSentToEISReport(subId2, None)
            MockEntryDeclarationRepo
              .lookupMetadata(subId2)
              .returns(Future.successful(Right(replayMetadata(subId2))))
            MockEisConnector.submitMetadata(metadata(subId2), bypassTrafficSwitch = true) returns Future.successful(
              None)
            MockReportSender.sendReport(successReport) returns Future.successful((): Unit)
            MockEntryDeclarationRepo.setEisSubmissionSuccess(subId2, Instant.now(clock)) returns Future.successful(true)

            service.replaySubmissions(submissionIds).futureValue shouldBe Right(BatchReplayResult(1, 1))
          }
        }
        "terminate and return Left" when {
          def abortEisSendFailure(eisSendFailure: EISSendFailure): Unit = {
            val submissionIds = Seq(subId1, subId2)

            MockEntryDeclarationRepo
              .lookupMetadata(subId1)
              .returns(Future.successful(Right(replayMetadata(subId1))))
            MockEisConnector
              .submitMetadata(metadata(subId1), bypassTrafficSwitch = true)
              .returns(Future.successful(Some(eisSendFailure)))
            MockReportSender
              .sendReport(submissionSentToEISReport(subId1, Some(eisSendFailure)))
              .returns(Future.successful((): Unit))

            service.replaySubmissions(submissionIds).futureValue match {
              case Left(Abort(BatchReplayError.EISSubmitError, Counts(_,f))) if f > 0 =>
              case _ => fail()
            }
          }
          "EISSendFailure is TrafficSwitchNotFlowing" in abortEisSendFailure(EISSendFailure.TrafficSwitchNotFlowing)
          "EISSendFailure is ExceptionThrown" in abortEisSendFailure(EISSendFailure.ExceptionThrown)
          "EISSendFailure is Timeout" in abortEisSendFailure(EISSendFailure.Timeout)
          "EISSendFailure is ErrorResponse(401)" in abortEisSendFailure(EISSendFailure.ErrorResponse(UNAUTHORIZED))
          "EISSendFailure is ErrorResponse(403)" in abortEisSendFailure(EISSendFailure.ErrorResponse(FORBIDDEN))
          "EISSendFailure is ErrorResponse(499)" in abortEisSendFailure(EISSendFailure.ErrorResponse(499))
          "EISSendFailure is ErrorResponse(500)" in
            abortEisSendFailure(EISSendFailure.ErrorResponse(INTERNAL_SERVER_ERROR))
          "EISSendFailure is ErrorResponse(503)" in
            abortEisSendFailure(EISSendFailure.ErrorResponse(SERVICE_UNAVAILABLE))
        }
      }

      "Sending the event unsuccessful" must {
        "terminate and return Left" in {
          val submissionIds = Seq(subId1, subId2)

          val report = submissionSentToEISReport(subId1, None)
          MockEntryDeclarationRepo.lookupMetadata(subId1) returns Future.successful(Right(replayMetadata(subId1)))
          MockEisConnector.submitMetadata(metadata(subId1), bypassTrafficSwitch = true) returns Future.successful(None)
          MockReportSender.sendReport(report) returns Future.failed(new IOException)
          MockEntryDeclarationRepo.setEisSubmissionSuccess(subId1, Instant.now(clock)) returns Future.successful(true)

          service.replaySubmissions(submissionIds).futureValue match {
            case Left(Abort(err, counts)) if err == BatchReplayError.EISEventError && counts.failureCount > 0 => succeed
            case _ => fail()
          }
        }
      }
    }

    "getting undelivered counts" must {
      "work" in {
        // WLOG - service just passes through from repo...
        val undeliveredCounts = UndeliveredCounts(totalCount = 123, transportCounts = None)

        MockEntryDeclarationRepo.getUndeliveredCounts returns Future.successful(undeliveredCounts)
        service.getUndeliveredCounts.futureValue shouldBe undeliveredCounts
      }
    }
  }
}
