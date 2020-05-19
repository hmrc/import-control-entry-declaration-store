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

package uk.gov.hmrc.entrydeclarationstore.validation.business

import uk.gov.hmrc.play.test.UnitSpec

class PathSpec extends UnitSpec {

  "Path" must {
    "parse absolute" in {
      Path.parse("/a/b/c") shouldBe Path.Absolute(Seq("a", "b", "c"))
    }
    "parse relative with ancestor navigation" in {
      Path.parse("../../a/b/c") shouldBe Path.Relative(2, Seq("a", "b", "c"))
    }
    "parse relative without ancestor navigation" in {
      Path.parse("a/b/c") shouldBe Path.Relative(0, Seq("a", "b", "c"))
    }
    "parse current" in {
      Path.parse(".") shouldBe Path.Current
    }
  }

}
