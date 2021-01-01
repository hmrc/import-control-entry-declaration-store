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

package uk.gov.hmrc.entrydeclarationstore.repositories

import play.api.libs.json.{Format, Json}

import java.time.Instant

//This allows consistent listing of the date time in ascending order. Instant writes drops 'insignificant zeros'
private[repositories] case class PersistableDateTime($date: Long) {
  def toInstant: Instant = Instant.ofEpochMilli($date)
}

private[repositories] object PersistableDateTime {
  implicit def format: Format[PersistableDateTime] = Json.format[PersistableDateTime]

  def apply(instant: Instant): PersistableDateTime =
    PersistableDateTime(instant.toEpochMilli)
}
