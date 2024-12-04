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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment, DeclarationRejectionEnrichment}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrichmentServiceSpec extends AnyWordSpec with MockEntryDeclarationRepo with ScalaFutures {

  val metrics: MetricRegistry = new MetricRegistry()

  val service = new EnrichmentService(mockEntryDeclarationRepo, metrics)

  val submissionId = "submissionId"

  "EnrichmentService" when {
    "retrieving an AccepatanceEnrichment" when {
      val acceptanceEnrichment = AcceptanceEnrichment(None, Json.parse("""{"hello" : "world"}"""))

      "it is found in the database" must {
        "return it" in {
          MockEntryDeclarationRepo.lookupAcceptanceEnrichment(submissionId) returns Future.successful(
            Some(acceptanceEnrichment))

          service.retrieveAcceptanceEnrichment(submissionId).futureValue shouldBe Some(acceptanceEnrichment)
        }
      }

      "it is not found in the database" must {
        "return None" in {
          MockEntryDeclarationRepo.lookupAcceptanceEnrichment(submissionId) returns Future.successful(None)

          service.retrieveAcceptanceEnrichment(submissionId).futureValue shouldBe None
        }
      }
    }
    "retrieving an AmendmentRejectionEnrichment" when {
      val rejectionEnrichment = AmendmentRejectionEnrichment(
        None,
        ResourceUtils.withInputStreamFor("jsons/AmendmentRejectionEnrichmentPayload.json")(Json.parse))

      "it is found in the database" must {
        "return it" in {
          MockEntryDeclarationRepo.lookupAmendmentRejectionEnrichment(submissionId) returns Future.successful(
            Some(rejectionEnrichment))

          service.retrieveAmendmentRejectionEnrichment(submissionId).futureValue shouldBe Some(rejectionEnrichment)
        }
      }

      "it is not found in the database" must {
        "return None" in {
          MockEntryDeclarationRepo.lookupAmendmentRejectionEnrichment(submissionId) returns Future.successful(None)

          service.retrieveAmendmentRejectionEnrichment(submissionId).futureValue shouldBe None
        }
      }
    }
    "retrieving an DeclarationRejectionEnrichment" when {
      val rejectionEnrichment = DeclarationRejectionEnrichment(None)

      "it is found in the database" must {
        "return it" in {
          MockEntryDeclarationRepo.lookupDeclarationRejectionEnrichment(submissionId) returns Future.successful(
            Some(rejectionEnrichment))

          service.retrieveDeclarationRejectionEnrichment(submissionId).futureValue shouldBe Some(rejectionEnrichment)
        }
      }

      "it is not found in the database" must {
        "return None" in {
          MockEntryDeclarationRepo.lookupDeclarationRejectionEnrichment(submissionId) returns Future.successful(None)

          service.retrieveDeclarationRejectionEnrichment(submissionId).futureValue shouldBe None
        }
      }
    }
  }

}
