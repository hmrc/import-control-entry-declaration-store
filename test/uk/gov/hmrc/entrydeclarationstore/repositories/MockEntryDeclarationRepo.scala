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

package uk.gov.hmrc.entrydeclarationstore.repositories

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.libs.json.JsValue
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models._

import java.time.Instant
import scala.concurrent.Future

trait MockEntryDeclarationRepo extends TestSuite with MockFactory {
  val mockEntryDeclarationRepo: EntryDeclarationRepo = mock[EntryDeclarationRepo]

  object MockEntryDeclarationRepo {
    def saveEntryDeclaration(declaration: EntryDeclarationModel): CallHandler[Future[Boolean]] =
      (mockEntryDeclarationRepo.save(_: EntryDeclarationModel)(_: LoggingContext)).expects(declaration, *)

    def lookupSubmissionIdAndReceivedDateTime(eori: String, correlationId: String): CallHandler[Future[Option[SubmissionIdLookupResult]]] =
      (mockEntryDeclarationRepo.lookupSubmissionId(_: String, _: String)).expects(eori, correlationId)

    def lookupEntryDeclaration(submissionId: String): CallHandler[Future[Option[JsValue]]] =
      mockEntryDeclarationRepo.lookupEntryDeclaration _ expects submissionId

    def setEisSubmissionSuccess(submissionId: String, time: Instant): CallHandler[Future[Boolean]] =
      (mockEntryDeclarationRepo
        .setEisSubmissionSuccess(_: String, _: Instant)(_: LoggingContext)).expects(submissionId, time, *)

    def setEisSubmissionFailure(submissionId: String): CallHandler[Future[Boolean]] =
      (mockEntryDeclarationRepo
        .setEisSubmissionFailure(_: String)(_: LoggingContext)).expects(submissionId, *)

    def lookupAcceptanceEnrichment(submissionId: String): CallHandler[Future[Option[AcceptanceEnrichment]]] =
      mockEntryDeclarationRepo.lookupAcceptanceEnrichment _ expects submissionId

    def lookupAmendmentRejectionEnrichment(
      submissionId: String): CallHandler[Future[Option[AmendmentRejectionEnrichment]]] =
      mockEntryDeclarationRepo.lookupAmendmentRejectionEnrichment _ expects submissionId

    def lookupDeclarationRejectionEnrichment(
      submissionId: String): CallHandler[Future[Option[DeclarationRejectionEnrichment]]] =
      mockEntryDeclarationRepo.lookupDeclarationRejectionEnrichment _ expects submissionId

    def lookupMetadata(submissionId: String): CallHandler[Future[Either[MetadataLookupError, ReplayMetadata]]] =
      (mockEntryDeclarationRepo.lookupMetadata(_: String)(_: LoggingContext)).expects(submissionId, *)

    def setHousekeepingAt(eori: String, correlationId: String, time: Instant): CallHandler[Future[Boolean]] =
      (mockEntryDeclarationRepo.setHousekeepingAt(_: String, _: String, _: Instant)).expects(eori, correlationId, time)

    def setHousekeepingAt(submissionId: String, time: Instant): CallHandler[Future[Boolean]] =
      (mockEntryDeclarationRepo.setHousekeepingAt(_: String, _: Instant)).expects(submissionId, time)

    def housekeep(time: Instant): CallHandler[Future[Int]] =
      mockEntryDeclarationRepo.housekeep _ expects time

    def totalUndeliveredMessages(receivedNoLaterThan: Instant): CallHandler[Future[Int]] =
      (mockEntryDeclarationRepo.totalUndeliveredMessages _).expects(receivedNoLaterThan)

    def getUndeliveredSubmissionIds(
      receivedNoLaterThan: Instant,
      limit: Option[Int]): CallHandler[Source[String, NotUsed]] =
      (mockEntryDeclarationRepo.getUndeliveredSubmissionIds _).expects(receivedNoLaterThan, limit)

    def getUndeliveredCounts: CallHandler[Future[UndeliveredCounts]] =
      (() => mockEntryDeclarationRepo.getUndeliveredCounts).expects()
  }

}
