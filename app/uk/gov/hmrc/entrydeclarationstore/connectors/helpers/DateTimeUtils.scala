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

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneId, ZonedDateTime}

import com.google.inject.Inject
import javax.inject.Singleton

@Singleton
class DateTimeUtils @Inject()(clock: Clock) {

  private def now: ZonedDateTime = ZonedDateTime.now(clock.withZone(ZoneId.of("GMT")))

  def currentDateInRFC1123Format: String =
    "Thu, 01 Oct 2020 12:06:21 GMT"
// FIXME ^^^ temporary fix to get over format problem with ...
//    DateTimeFormatter.RFC_1123_DATE_TIME.format(now)
// needs to comply with https://tools.ietf.org/html/rfc7231 section 7.1.1.1
}
