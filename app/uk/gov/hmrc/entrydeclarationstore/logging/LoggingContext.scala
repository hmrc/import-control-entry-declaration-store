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

package uk.gov.hmrc.entrydeclarationstore.logging

case class LoggingContext(
  eori: Option[String]           = None,
  correlationId: Option[String]  = None,
  submissionId: Option[String]   = None,
  mrn: Option[String]            = None,
  optMessageType: Option[String] = None
) {
  private[logging] lazy val context: String = {
    val props = Seq(
      eori.map(v => s"eori=$v"),
      correlationId.map(v => s"correlationId=$v"),
      submissionId.map(v => s"submissionId=$v"),
      mrn.map(v => s"movementReferenceNumber=$v")
    ).flatten.mkString(start = "(", sep = " ", end = ")")

    optMessageType match {
      case Some(messageType) => s"$messageType $props"
      case None              => props
    }
  }
}

object LoggingContext {
  def apply(eori: String, correlationId: String, submissionId: String): LoggingContext =
    LoggingContext(eori = Some(eori), correlationId = Some(correlationId), submissionId = Some(submissionId))

  def withMessageType(eori: String, correlationId: String, submissionId: String, mrn: Option[String]): LoggingContext =
    LoggingContext(
      eori           = Some(eori),
      correlationId  = Some(correlationId),
      submissionId   = Some(submissionId),
      mrn            = mrn,
      optMessageType = Some(messageType(mrn.isDefined)))

  private def messageType(hasMrn: Boolean) =
    if (hasMrn) "CC313A" else "CC315A"
}
