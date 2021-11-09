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

package uk.gov.hmrc.entrydeclarationstore.nrs

import com.kenshoo.play.metrics.Metrics
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.utils.MockMetrics
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NRSServiceSpec extends AnyWordSpec with MockNRSConnector with NRSMetadataTestData with ScalaFutures {

  val metrics: Metrics = new MockMetrics

  val service = new NRSService(mockNRSConnector, metrics)

  val nrsSubmission: NRSSubmission = NRSSubmission(nrsMetadataRawPayload, nrsMetadata)

  implicit val hc: HeaderCarrier  = HeaderCarrier()
  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  "NRSService" when {
    "NRS submission is successful" must {
      "return the response" in {
        val response = NRSResponse("someId")
        MockNRSConnector.submit(nrsSubmission) returns Future.successful(Right(response))

        service.submit(nrsSubmission).futureValue shouldBe Some(response)
      }

      "NRS submission fails" must {
        "return None" in {
          // WLOG
          val failure = NRSSubmisionFailure.ExceptionThrown

          MockNRSConnector.submit(nrsSubmission) returns Future.successful(Left(failure))

          service.submit(nrsSubmission).futureValue shouldBe None
        }
      }
    }
  }
}
