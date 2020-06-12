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

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.http.Status.BAD_REQUEST
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, EisConnector}
import uk.gov.hmrc.entrydeclarationstore.models.{ReplayError, ReplayMetadata, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.reporting.{ReportSender, SubmissionSentToEIS}
import uk.gov.hmrc.entrydeclarationstore.repositories.{EntryDeclarationRepo, MetadataLookupError}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubmissionReplayService @Inject()(
  entryDeclarationRepo: EntryDeclarationRepo,
  eisConnector: EisConnector,
  reportSender: ReportSender)(implicit ec: ExecutionContext) {

  case class Abort(error: ReplayError)
  case class Counts(successCount: Int, failureCount: Int)

  def replaySubmission(submissionIds: Seq[String])(
    implicit hc: HeaderCarrier): Future[Either[ReplayError, ReplayResult]] =
    submissionIds
      .foldLeft(Future.successful(Counts(0, 0).asRight[Abort]): Future[Either[Abort, Counts]]) { (acc, submissionId) =>
        acc.flatMap {
          case Right(counts)              => replaySubmissionId(submissionId, counts)
          case abort: Left[Abort, Counts] => Future.successful(abort)
        }
      }
      .map {
        case Right(counts) => Right(ReplayResult(counts.successCount, counts.failureCount))
        case Left(abort)   => Left(abort.error)
      }
      .recover {
        case _: ReactiveMongoException => Left(ReplayError.MetadataRetrievalError)
      }

  private def replaySubmissionId(submissionId: String, state: Counts)(
    implicit hc: HeaderCarrier): Future[Either[Abort, Counts]] = {
    val result = for {
      replayMetadata <- EitherT(doMetadataLookup(submissionId))
      sendSuccess    <- EitherT(doEisSubmit(replayMetadata))
    } yield {
      if (sendSuccess) {
        Counts(state.successCount + 1, state.failureCount)
      } else {
        Counts(state.successCount, state.failureCount + 1)
      }
    }
    result.value
  }

  private def doMetadataLookup(submissionId: String): Future[Either[Abort, Option[ReplayMetadata]]] =
    entryDeclarationRepo
      .lookupMetadata(submissionId)
      .map {
        case Right(metadata)                            => Right(Some(metadata))
        case Left(MetadataLookupError.MetadataNotFound) => Right(None)
        case Left(MetadataLookupError.DataFormatError)  => Right(None)
      }
      .recover {
        case _: ReactiveMongoException => Left(Abort(ReplayError.MetadataRetrievalError))
      }

  private def doEisSubmit(optionReplayMetadata: Option[ReplayMetadata])(
    implicit hc: HeaderCarrier): Future[Either[Abort, Boolean]] =
    optionReplayMetadata match {
      case Some(replayMetadata) =>
        for {
          replayError <- eisConnector.submitMetadata(replayMetadata.metadata)
          eventSent   <- sendEvent(replayMetadata, replayError)
        } yield {
          if (eventSent) {
            replayError match {
              case None                                            => Right(true)
              case Some(EISSendFailure.ErrorResponse(BAD_REQUEST)) => Right(false)
              case Some(_)                                         => Left(Abort(ReplayError.EISSubmitError))
            }
          } else {
            Left(Abort(ReplayError.EISEventError))
          }
        }
      case None => Future.successful(Right(false))
    }

  private def sendEvent(replayMetadata: ReplayMetadata, eISSendFailure: Option[EISSendFailure])(
    implicit hc: HeaderCarrier): Future[Boolean] =
    reportSender
      .sendReport(
        SubmissionSentToEIS(
          replayMetadata.eori,
          replayMetadata.correlationId,
          replayMetadata.metadata.submissionId,
          replayMetadata.metadata.messageType,
          eISSendFailure))
      .map(_ => true)
      .recover { case NonFatal(_) => false }
}
