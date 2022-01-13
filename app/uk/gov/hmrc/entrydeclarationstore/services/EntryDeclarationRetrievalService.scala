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

package uk.gov.hmrc.entrydeclarationstore.services

import play.api.libs.json.JsValue
import uk.gov.hmrc.entrydeclarationstore.models.SubmissionIdLookupResult
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class EntryDeclarationRetrievalService @Inject()(entryDeclarationRepo: EntryDeclarationRepo) {

  // To facilitate retrieval testing when armed with an eori and a correlationId
  def retrieveSubmissionIdAndReceivedDateTime(
    eori: String,
    correlationId: String): Future[Option[SubmissionIdLookupResult]] =
    entryDeclarationRepo.lookupSubmissionId(eori, correlationId)

  def retrieveSubmission(submissionId: String): Future[Option[JsValue]] =
    entryDeclarationRepo.lookupEntryDeclaration(submissionId)
}
