/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.http.Status._
import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats

/**
  * Error that conforms to the standard API platform error structure.
  */
case class StandardError(status: Int, code: String, message: String)

object StandardError {
  implicit def xmlFormats[A <: StandardError]: XmlFormats[A] =
    (a: A) =>
      // @formatter:off
    <error>
      <code>{a.code}</code>
      <message>{a.message}</message>
    </error>
  // @formatter:on

  object Unauthorized extends StandardError(UNAUTHORIZED, "UNAUTHORIZED", "Permission denied")
}
