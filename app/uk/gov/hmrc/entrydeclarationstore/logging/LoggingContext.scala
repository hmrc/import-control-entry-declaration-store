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

package uk.gov.hmrc.entrydeclarationstore.logging

case class LoggingContext(
  eori: Option[String]          = None,
  correlationId: Option[String] = None,
  submissionId: Option[String]  = None,
  mrn: Option[Option[String]]   = None
) {
  private[logging] lazy val context: String = {
    val messageTypeString = mrn.map(_.map(_ => "CC313A").getOrElse("CC315A"))
    Seq(
      messageTypeString,
      eori.map(v => s"(eori=$v"),
      correlationId.map(v => s"correlationId=$v"),
      submissionId.map(v => s"submissionId=$v"),
      mrn.flatMap(_.map(v => s"movementReferenceNumber=$v"))
    ).flatten.mkString(start = "", sep = " ", end = ")")
  }
}

object LoggingContext {
  def apply(eori: String, correlationId: String, submissionId: String): LoggingContext =
    LoggingContext(Some(eori), Some(correlationId), Some(submissionId))

  def apply(eori: String, correlationId: String, submissionId: String, mrn: Option[String]): LoggingContext =
    LoggingContext(Some(eori), Some(correlationId), Some(submissionId), Some(mrn))
}
