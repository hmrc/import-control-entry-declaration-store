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

package uk.gov.hmrc.entrydeclarationstore.utils

import org.scalatest.{MustMatchers, WordSpec}
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._

class ChecksumUtilsSpec extends WordSpec with MustMatchers {
  "StringWithSha256" should {

    "digest sha-256" in {
      "FooBar1".getBytes("UTF-8").calculateSha256 must ===(
        "26a9f5a12e997b4dcfaefa87378e2a84500991a9befc13c774466415005182ca")
      "Foobar1".getBytes("UTF-8").calculateSha256 must ===(
        "ee16369f2e7f155a12a83c72c237e46ca3cb5ce068a339f2889835eb08448d76")
    }

    "digest sha256 with leading zero" in {
      "39".getBytes("UTF-8").calculateSha256 mustBe "0b918943df0962bc7a1824c0555a389347b4febdc7cf9d1254406d80ce44e3f9"
    }
  }
}
