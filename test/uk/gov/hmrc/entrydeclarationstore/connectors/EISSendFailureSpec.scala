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

package uk.gov.hmrc.entrydeclarationstore.connectors

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class EISSendFailureSpec extends AnyWordSpec {

  "EISSendFailure" must {
    "write to Json correctly" in {
      Json.toJson(EISSendFailure.TrafficSwitchNotFlowing : EISSendFailure) shouldBe
        Json.parse("""
                     |{
                     |  "type": "TRAFFIC_SWITCH_NOT_FLOWING"
                     |}
                     |""".stripMargin)

      Json.toJson(EISSendFailure.ErrorResponse(123) : EISSendFailure) shouldBe
        Json.parse("""
                     |{
                     |  "type": "ERROR_RESPONSE",
                     |  "status": 123
                     |}
                     |""".stripMargin)

      Json.toJson(EISSendFailure.ExceptionThrown : EISSendFailure) shouldBe
        Json.parse("""
                     |{
                     |  "type": "EXCEPTION_THROWN"
                     |}
                     |""".stripMargin)

      Json.toJson(EISSendFailure.Timeout : EISSendFailure) shouldBe
        Json.parse("""
                     |{
                     |  "type": "TIMEOUT"
                     |}
                     |""".stripMargin)
    }
  }

}
