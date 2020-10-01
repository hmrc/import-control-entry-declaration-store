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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import java.time.{Clock, LocalDateTime, ZoneId, ZonedDateTime}

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar

class DateTimeUtilsSpec extends WordSpec with Matchers with MockitoSugar {

  val time: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2020, 10, 1, 12, 6, 21), ZoneId.of("+00"))
  val mockClock: Clock    = Clock.fixed(time.toInstant, time.getZone)
  val dateTimeUtils       = new DateTimeUtils(mockClock)

  "currentDateInRFC1123Format" must {
    "return date in RFC_1123_DATE_TIME format" in {
      dateTimeUtils.currentDateInRFC1123Format shouldBe "Thu, 01 Oct 2020 12:06:21 GMT"
    }
  }

}
