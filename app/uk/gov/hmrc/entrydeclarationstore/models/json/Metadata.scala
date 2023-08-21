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

package uk.gov.hmrc.entrydeclarationstore.models.json

import cats.syntax.all._
import com.lucidchart.open.xtract.XmlReader._
import com.lucidchart.open.xtract.{XmlReader, __}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.entrydeclarationstore.models.{InstantFormatter, MessageType}

import scala.util.matching.Regex

case class Metadata(
  senderEORI: String,
  senderBranch: String,
  preparationDateTime: String,
  messageType: MessageType,
  receivedDateTime: String,
  messageIdentification: String,
  correlationId: String
)

object Metadata extends InstantFormatter {
  implicit def reader(implicit input: InputParameters): XmlReader[Metadata] =
    (
      (__ \ "MesSenMES3").read[String],
      (__ \ "DatOfPreMES9").read[String],
      (__ \ "TimOfPreMES10").read[String],
      (__ \ "MesIdeMES19").read[String]
    ).mapN {
      (senderEoriAndBranch: String, preparationDate: String, preparationTime: String, messageIdentification: String) =>
        // Level 2 validation mandates a '/' character separating eori and branch
        val parts          = senderEoriAndBranch.split('/')
        val (eori, branch) = (parts.head.trim, parts.drop(1).head.trim)
        //Correlation Id is populated by the auto-generated correlationId and NOT the CorIdeMES25 field.
        Metadata(
          eori,
          branch,
          dateTime(preparationDate, preparationTime),
          MessageType(amendment = input.isAmendment),
          dateTimeWithMillis.format(input.receivedDateTime),
          messageIdentification,
          input.correlationId
        )
    }

  implicit val writes: Writes[Metadata] = Json.writes[Metadata]
  val dateRegex: Regex                  = "([0-9]{2})([0-9]{2})([0-9]{2})".r
  val timeRegex: Regex                  = "([0-9]{2})([0-9]{2})".r

  def dateTime(date: String, time: String): String = {
    val formattedDate = date match {
      case dateRegex(year, month, day) => s"20$year-$month-$day"
      case _ => date
    }
    val formattedTime = time match {
      case timeRegex(hour, minute) => s"T$hour:$minute:00.000Z"
      case _ => time
    }
    formattedDate + formattedTime
  }
}
