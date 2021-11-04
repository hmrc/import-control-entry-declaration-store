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

package uk.gov.hmrc.entrydeclarationstore.connectors.helpers

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Inspectors, Matchers, WordSpec}

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateTimeUtilsSpec extends WordSpec with Matchers with Inspectors {

  implicit val arbInstant: Arbitrary[Instant] = Arbitrary(
    for {
      year  <- Gen.choose(2000, 2100)
      month <- Gen.choose(1, 12)
      dom   <- Gen.choose(1, 28)
      hour  <- Gen.choose(0, 23)
      min   <- Gen.choose(0, 59)
      sec   <- Gen.choose(0, 59)
    } yield {
      val time: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(year, month, dom, hour, min, sec), ZoneOffset.UTC)
      time.toInstant
    }
  )

  val instants: Gen[Instant] = for {
    year  <- Gen.choose(2000, 2100)
    month <- Gen.choose(1, 12)
    dom   <- Gen.choose(1, 28)
    hour  <- Gen.choose(0, 23)
    min   <- Gen.choose(0, 59)
    sec   <- Gen.choose(0, 59)
  } yield {
    val time: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(year, month, dom, hour, min, sec), ZoneOffset.UTC)
    time.toInstant
  }

  "currentDateInRFC1123Format" must {
    "return correct string for a known date" in {
      val time: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2020, 10, 2, 16, 5, 15), ZoneOffset.UTC)
      time.toInstant

      DateTimeUtils.httpDateFormatFor(time.toInstant) shouldBe "Fri, 02 Oct 2020 16:05:15 GMT"
    }

    "return a string in the rfc7231 HTTP-date format" in {
      val dateRegex =
        "(Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT".r

      instants.map { instant =>
        val httpDate = DateTimeUtils.httpDateFormatFor(instant)

        httpDate should fullyMatch regex dateRegex
      }
    }

    "return a string that parses (with an English locale) back to the same time" in {
      val simpleFormat =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneOffset.UTC)

      instants.map { instant =>
        val httpDate = DateTimeUtils.httpDateFormatFor(instant)

        ZonedDateTime.parse(httpDate, simpleFormat).toInstant shouldBe instant
      }
    }
  }
}
