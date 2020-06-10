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

package uk.gov.hmrc.entrydeclarationstore.repositories

import java.time.Instant

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import play.api.libs.json.JsValue
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment, EntryDeclarationMetadata, EntryDeclarationModel, SubmissionIdLookupResult}

import scala.concurrent.Future

trait MockEntryDeclarationRepo extends MockFactory {
  val mockEntryDeclarationRepo: EntryDeclarationRepo = mock[EntryDeclarationRepo]

  object MockEntryDeclarationRepo {
    def saveEntryDeclaration(declation: EntryDeclarationModel): CallHandler[Future[Boolean]] =
      mockEntryDeclarationRepo.save _ expects declation

    def lookupSubmissionIdAndReceivedDateTime(
      eori: String,
      correlationId: String): CallHandler[Future[Option[SubmissionIdLookupResult]]] =
      mockEntryDeclarationRepo.lookupSubmissionId _ expects (eori, correlationId)

    def lookupEntryDeclaration(submissionId: String): CallHandler[Future[Option[JsValue]]] =
      mockEntryDeclarationRepo.lookupEntryDeclaration _ expects submissionId

    def setSubmissionTime(submissionId: String, time: Instant): CallHandler[Future[Boolean]] =
      mockEntryDeclarationRepo.setSubmissionTime _ expects (submissionId, time)

    def lookupAcceptanceEnrichment(submissionId: String): CallHandler[Future[Option[AcceptanceEnrichment]]] =
      mockEntryDeclarationRepo.lookupAcceptanceEnrichment _ expects submissionId

    def lookupAmendmentRejectionEnrichment(
      submissionId: String): CallHandler[Future[Option[AmendmentRejectionEnrichment]]] =
      mockEntryDeclarationRepo.lookupAmendmentRejectionEnrichment _ expects submissionId

    def lookupMetadata(
      submissionId: String): CallHandler[Future[Either[MetadataLookupError, EntryDeclarationMetadata]]] =
      mockEntryDeclarationRepo.lookupMetadata _ expects submissionId
  }

}
