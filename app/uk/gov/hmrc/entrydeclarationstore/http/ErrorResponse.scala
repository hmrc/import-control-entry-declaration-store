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

package uk.gov.hmrc.entrydeclarationstore.http

import uk.gov.hmrc.entrydeclarationstore.utils.XmlFormats

import scala.xml.Node

case class ErrorResponse(
  statusCode: Int,
  message: String,
  requested: Option[String] = None
)

object ErrorResponse {
  implicit val xmlFormats: XmlFormats[ErrorResponse] = new XmlFormats[ErrorResponse] {
    override def toXml(a: ErrorResponse): Node =
      // @formatter:off
      <error>
        <statusCode>{a.statusCode}</statusCode>
        <message>{a.message}</message>
        { for(value <- a.requested.toSeq) yield <requested>{value}</requested> }
      </error>
    // @formatter:on
  }
}
