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

package uk.gov.hmrc.entrydeclarationstore.models

import org.scalatest.Inspectors
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.play.test.UnitSpec

class EisSubmissionStateSpec extends UnitSpec with Inspectors {
  import EisSubmissionState._
  "EisSubmissionState" must {
    "serialize to JSON correctly" in {
      Json.toJson(Sent)    shouldBe JsString("sent")
      Json.toJson(NotSent) shouldBe JsString("not-sent")
      Json.toJson(Error)   shouldBe JsString("error")
    }

    "round trip correctly back to the object" in {
      forAll(List(Sent, NotSent, Error)) { value =>
        Json.toJson(value).as[EisSubmissionState] shouldBe value
      }
    }

    // So that we can reference the object in e.g. BSONDocument without hard-coding the string
    "know its own format string" in {
      mongoFormatString(Sent)    shouldBe "sent"
      mongoFormatString(NotSent) shouldBe "not-sent"
      mongoFormatString(Error)   shouldBe "error"
    }
  }
}
