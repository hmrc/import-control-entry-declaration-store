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

import javax.inject.{Inject, Singleton}
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.entrydeclarationstore.connectors.{EISSendFailure, EisConnector}
import uk.gov.hmrc.entrydeclarationstore.models.{EntryDeclarationMetadata, ReplayError, ReplayResult}
import uk.gov.hmrc.entrydeclarationstore.reporting.ReportSender
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionReplayService @Inject()(
  entryDeclarationRepo: EntryDeclarationRepo,
  eisConnector: EisConnector,
  reportSender: ReportSender)(implicit ec: ExecutionContext) {
  def replaySubmission(submissionIds: Seq[String]): Future[Either[ReplayError, ReplayResult]] = {
    //perhaps a for comprehension
    //Left maps to ReplayError, right needs to hold success/failure? Option[Success]?
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val submissionId: String       = "abc" //change to foreach
    for {
      metadata <- getMetadata(submissionId)
//      _ <- submitToEis(metadata.right.getOrElse(Right(None)))
    } yield metadata
  }
//try akka streams using list as source
  private def getMetadata(submissionId: String): Future[Either[ReplayError, Option[EntryDeclarationMetadata]]] =
    entryDeclarationRepo.lookupMetadata(submissionId).map {
      case Right(metadata) => Right(Some(metadata))
      case Left(_)         => Right(None)
    }

  private def submitToEis(metadata: EntryDeclarationMetadata)(
    implicit hc: HeaderCarrier): Future[Either[ReplayError, Option[Unit]]] =
    eisConnector
      .submitMetadata(metadata)
      .map {
        case None                                            => Right(Some((): Unit))
        case Some(EISSendFailure.ErrorResponse(BAD_REQUEST)) => Right(None)
        case _                                               => Left(ReplayError.EISSubmitError)
      }
}
