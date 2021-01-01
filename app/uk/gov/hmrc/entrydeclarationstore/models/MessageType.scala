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

package uk.gov.hmrc.entrydeclarationstore.models

import play.api.libs.json.Format
import uk.gov.hmrc.entrydeclarationstore.utils.Enums

sealed trait MessageType

object MessageType {

  case object IE315 extends MessageType

  case object IE313 extends MessageType

  implicit val formats: Format[MessageType] = Enums.format[MessageType]

  def apply(amendment: Boolean): MessageType =
    if (amendment) MessageType.IE313 else MessageType.IE315
}
