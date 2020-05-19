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

import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment}
import uk.gov.hmrc.entrydeclarationstore.repositories.EntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.{EventLogger, Timer}

import scala.concurrent.{ExecutionContext, Future}

class EnrichmentService @Inject()(entryDeclarationRepo: EntryDeclarationRepo, override val metrics: Metrics)(
  implicit ec: ExecutionContext)
    extends Timer
    with EventLogger {

  def retrieveAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]] =
    timeFuture("Service retrieveAcceptanceEnrichment", "retrieveAcceptanceEnrichment.total") {
      entryDeclarationRepo.lookupAcceptanceEnrichment(submissionId)
    }

  def retrieveAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]] =
    timeFuture("Service retrieveRejectionEnrichment", "retrieveRejectionEnrichment.total") {
      entryDeclarationRepo.lookupAmendmentRejectionEnrichment(submissionId)
    }
}
