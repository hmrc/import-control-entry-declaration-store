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

import cats.data.EitherT
import cats.implicits._
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, EisConnector}
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.{Counts, BatchReplayError, BatchReplayResult, Abort, ReplayMetadata, UndeliveredCounts}
import uk.gov.hmrc.entrydeclarationstore.reporting.{ReportSender, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.{EntryDeclarationRepo, MetadataLookupError}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubmissionReplayService @Inject()(
  entryDeclarationRepo: EntryDeclarationRepo,
  eisConnector: EisConnector,
  reportSender: ReportSender,
  clock: Clock)(implicit ec: ExecutionContext) {
  def getUndeliveredCounts: Future[UndeliveredCounts] = entryDeclarationRepo.getUndeliveredCounts

  def replaySubmissions(submissionIds: Seq[String])(implicit hc: HeaderCarrier): Future[Either[Abort, BatchReplayResult]] =
    submissionIds
      .foldLeft(Future.successful(Counts(0, 0).asRight[Abort]): Future[Either[Abort, Counts]]) { (acc, submissionId) =>
        acc.flatMap {
          case Right(counts) => replaySubmissionId(submissionId, counts)
          case abort         => Future.successful(abort)
        }
      }
      .map {
        case Right(counts) => Right(BatchReplayResult(counts.successCount, counts.failureCount))
        case Left(abort)   => Left(abort)
      }

  private def replaySubmissionId(submissionId: String, state: Counts)(
    implicit hc: HeaderCarrier): Future[Either[Abort, Counts]] = {
    implicit val lc: LoggingContext = LoggingContext(submissionId = Some(submissionId))

    {for {
      replayMetadata <- EitherT(doMetadataLookup(submissionId))
      sendSuccess    <- EitherT(doEisSubmit(replayMetadata, submissionId))
    } yield {
      if (sendSuccess) {
        ContextLogger.info("Replay submission Success")
        Counts(state.successCount + 1, state.failureCount)
      } else {
        ContextLogger.info("Replay submission Failed")
        Counts(state.successCount, state.failureCount + 1)
      }
    }}.value.map{
      case Left(error) => Left(Abort(error, Counts(state.successCount, state.failureCount + 1)))
      case Right(result) => Right(result)
    }.recover {
      case e => Left(Abort(BatchReplayError.MetadataRetrievalError,
                           Counts(state.successCount, state.failureCount + 1)))
    }

  }

  private def doMetadataLookup(submissionId: String): Future[Either[BatchReplayError, Option[ReplayMetadata]]] = {
    implicit val lc: LoggingContext = LoggingContext(submissionId = Some(submissionId))

    ContextLogger.info(s"Replaying submission $submissionId")

    entryDeclarationRepo
      .lookupMetadata(submissionId)
      .map {
        case Right(metadata)                            => Right(Some(metadata))
        case Left(MetadataLookupError.MetadataNotFound) => Right(None)
        case Left(MetadataLookupError.DataFormatError)  => Right(None)
      }.recover {
        case _ => Left(BatchReplayError.MetadataRetrievalError)
      }
  }

  private def doEisSubmit(optionReplayMetadata: Option[ReplayMetadata], submissionId: String)
                         (implicit hc: HeaderCarrier): Future[Either[BatchReplayError, Boolean]] =
    optionReplayMetadata match {
      case Some(replayMetadata) =>
        implicit val lc: LoggingContext = LoggingContext(
          eori          = replayMetadata.eori,
          correlationId = replayMetadata.correlationId,
          submissionId  = submissionId)

        for {
          replayError <- eisConnector.submitMetadata(replayMetadata.metadata, bypassTrafficSwitch = true)
          eventSent   <- sendEvent(replayMetadata, replayError)
          _           <- setSubmissionState(submissionId, replayError)
        } yield {
          if (eventSent) {
            replayError match {
              case None                                            => Right(true)
              case Some(EISSendFailure.ErrorResponse(BAD_REQUEST)) => Right(false)
              case Some(_)                                         => Left(BatchReplayError.EISSubmitError)
            }
          } else {
            Left(BatchReplayError.EISEventError)
          }
        }
      case None => Future.successful(Right(false))
    }

  private def setSubmissionState(submissionId: String, eisSendFailure: Option[EISSendFailure])(
    implicit lc: LoggingContext): Future[Boolean] =
    eisSendFailure match {
      case None    => entryDeclarationRepo.setEisSubmissionSuccess(submissionId, Instant.now(clock))
      case Some(_) => Future.successful(false)
    }
  private def sendEvent(replayMetadata: ReplayMetadata, eisSendFailure: Option[EISSendFailure])(
    implicit hc: HeaderCarrier,
    lc: LoggingContext): Future[Boolean] =
    reportSender
      .sendReport(
        SubmissionSentToEIS(
          replayMetadata.eori,
          replayMetadata.correlationId,
          replayMetadata.metadata.submissionId,
          replayMetadata.metadata.messageType,
          eisSendFailure))
      .map(_ => true)
      .recover { case NonFatal(_) => false }
}
