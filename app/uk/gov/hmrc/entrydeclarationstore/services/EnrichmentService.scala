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

import com.codahale.metrics.MetricRegistry
import play.api.Logging
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment, DeclarationRejectionEnrichment}
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.Timer

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EnrichmentService @Inject()(entryDeclarationRepo: EntryDeclarationRepo, override val metrics: MetricRegistry)(
  implicit ec: ExecutionContext)
    extends Timer
    with Logging {

  def retrieveAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]] =
    timeFuture("Service retrieveAcceptanceEnrichment", "retrieveAcceptanceEnrichment.total") {
      entryDeclarationRepo.lookupAcceptanceEnrichment(submissionId)
    }

  def retrieveAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]] =
    timeFuture("Service retrieveRejectionEnrichment", "retrieveAmendmentRejectionEnrichment.total") {
      entryDeclarationRepo.lookupAmendmentRejectionEnrichment(submissionId)
    }

  def retrieveDeclarationRejectionEnrichment(submissionId: String): Future[Option[DeclarationRejectionEnrichment]] =
    timeFuture("Service retrieveRejectionEnrichment", "retrieveDeclarationRejectionEnrichment.total") {
      entryDeclarationRepo.lookupDeclarationRejectionEnrichment(submissionId)
    }
}
