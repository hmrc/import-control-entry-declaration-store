/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.controllers

import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment, DeclarationRejectionEnrichment}
import uk.gov.hmrc.entrydeclarationstore.services.MockEnrichmentService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrichmentControllerSpec extends WordSpec with MockEnrichmentService {

  private val controller = new EnrichmentController(Helpers.stubControllerComponents(), mockEnrichmentService)

  val submissionId                               = "submissionId"
  val acceptanceEnrichment: AcceptanceEnrichment = AcceptanceEnrichment(None, Json.parse("""{"hello" : "world"}"""))
  val amendmentRejectionEnrichment: AmendmentRejectionEnrichment =
    AmendmentRejectionEnrichment(None, Json.parse("""{"hello" : "world"}"""))
  val declarationRejectionEnrichment: DeclarationRejectionEnrichment = DeclarationRejectionEnrichment(None)

  "EnrichmentController" when {
    "getting AcceptanceEnrichment from submissionId" when {
      "id exists" must {
        "return OK with the AcceptanceEnrichment in a JSON object" in {
          MockEnrichmentService
            .retrieveAcceptanceEnrichment(submissionId)
            .returns(Future.successful(Some(acceptanceEnrichment)))

          val result: Future[Result] = controller.getAcceptanceEnrichment(submissionId)(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(acceptanceEnrichment)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }

      "id does not exist" must {
        "return NOT_FOUND" in {
          MockEnrichmentService.retrieveAcceptanceEnrichment(submissionId).returns(Future.successful(None))

          val result: Future[Result] = controller.getAcceptanceEnrichment(submissionId)(FakeRequest())

          status(result) shouldBe NOT_FOUND
        }
      }
    }
    "getting AmendmentRejectionEnrichment from submissionId" when {
      "id exists" must {
        "return OK with the AmendmentRejectionEnrichment in a JSON object" in {
          MockEnrichmentService
            .retrieveAmendmentRejectionEnrichment(submissionId)
            .returns(Future.successful(Some(amendmentRejectionEnrichment)))

          val result: Future[Result] = controller.getAmendmentRejectionEnrichment(submissionId)(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(amendmentRejectionEnrichment)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }

      "id does not exist" must {
        "return NOT_FOUND" in {
          MockEnrichmentService.retrieveAmendmentRejectionEnrichment(submissionId).returns(Future.successful(None))

          val result: Future[Result] = controller.getAmendmentRejectionEnrichment(submissionId)(FakeRequest())

          status(result) shouldBe NOT_FOUND
        }
      }
    }
    "getting DeclarationRejectionEnrichment from submissionId" when {
      "id exists" must {
        "return OK with the DeclarationRejectionEnrichment in a JSON object" in {
          MockEnrichmentService
            .retrieveDeclarationRejectionEnrichment(submissionId)
            .returns(Future.successful(Some(declarationRejectionEnrichment)))

          val result: Future[Result] = controller.getDeclarationRejectionEnrichment(submissionId)(FakeRequest())

          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(declarationRejectionEnrichment)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }

      "id does not exist" must {
        "return NOT_FOUND" in {
          MockEnrichmentService.retrieveDeclarationRejectionEnrichment(submissionId).returns(Future.successful(None))

          val result: Future[Result] = controller.getDeclarationRejectionEnrichment(submissionId)(FakeRequest())

          status(result) shouldBe NOT_FOUND
        }
      }
    }
  }
}
