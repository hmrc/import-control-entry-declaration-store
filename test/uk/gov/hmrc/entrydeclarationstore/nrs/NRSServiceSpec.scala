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

package uk.gov.hmrc.entrydeclarationstore.nrs

import com.kenshoo.play.metrics.Metrics
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.utils.MockMetrics
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class NRSServiceSpec
    extends UnitSpec
    with MockNRSConnector
    with MockAppConfig
    with NRSMetadataTestData
    with ScalaFutures {

  val metrics: Metrics = new MockMetrics

  val service = new NRSService(mockNRSConnector, mockAppConfig, metrics)

  val nrsSubmission: NRSSubmission = NRSSubmission("payload", nrsMetadata)

  implicit val hc: HeaderCarrier  = HeaderCarrier()
  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  "NRSService" when {
    "NRS submission is successful" must {
      "return the response" in {
        val response = NRSResponse("someId")
        MockAppConfig.nrsEnabled returns true
        MockNRSConnector.submit(nrsSubmission) returns Right(response)

        service.submit(nrsSubmission).futureValue shouldBe Some(response)
      }

      "NRS submission fails" must {
        "return None" in {
          // WLOG
          val failure = NRSSubmisionFailure.ExceptionThrown

          MockAppConfig.nrsEnabled returns true
          MockNRSConnector.submit(nrsSubmission) returns Left(failure)

          service.submit(nrsSubmission).futureValue shouldBe None
        }
      }

      "NRS submission is disabled" must {
        "return None" in {
          MockAppConfig.nrsEnabled returns false
          service.submit(nrsSubmission).futureValue shouldBe None
        }
      }
    }
  }
}
