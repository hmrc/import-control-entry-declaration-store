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
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment}
import uk.gov.hmrc.entrydeclarationstore.repositories.MockEntryDeclarationRepo
import uk.gov.hmrc.entrydeclarationstore.utils.{MockMetrics, ResourceUtils}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrichmentServiceSpec extends UnitSpec with MockEntryDeclarationRepo with ScalaFutures {

  val mockedMetrics: Metrics = new MockMetrics

  val service = new EnrichmentService(mockEntryDeclarationRepo, mockedMetrics)

  val submissionId = "submissionId"

  "EnrichmentService" when {
    "retrieving an AccepatanceEnrichment" when {
      val acceptanceEnrichment = AcceptanceEnrichment(Json.parse("""{"hello" : "world"}"""))

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
  }

}
