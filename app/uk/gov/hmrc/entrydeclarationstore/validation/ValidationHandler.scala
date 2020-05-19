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

package uk.gov.hmrc.entrydeclarationstore.validation

import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.ErrorWrapper
import uk.gov.hmrc.entrydeclarationstore.services.MRNMismatchError
import uk.gov.hmrc.entrydeclarationstore.utils.{EventLogger, Timer, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation.business.RuleValidator
import uk.gov.hmrc.entrydeclarationstore.validation.schema.{SchemaTypeE313, SchemaTypeE315, SchemaValidator}

import scala.xml.NodeSeq

trait ValidationHandler {
  def handleValidation(payload: String, mrn: Option[String]): Either[ErrorWrapper[_], NodeSeq]
}

class ValidationHandlerImpl @Inject()(
  schemaValidator: SchemaValidator,
  @Named("ruleValidator313") ruleValidator313: RuleValidator,
  @Named("ruleValidator315") ruleValidator315: RuleValidator,
  override val metrics: Metrics,
  appConfig: AppConfig)
    extends ValidationHandler
    with Timer
    with EventLogger {

  implicit val xmlFormatConfig: XmlFormatConfig = appConfig.xmlFormatConfig

  def handleValidation(payload: String, mrn: Option[String]): Either[ErrorWrapper[_], NodeSeq] =
    for {
      xmlPayload <- validateSchema(payload, mrn)
      _          <- checkMrn(xmlPayload, mrn)
      _          <- validateRules(xmlPayload, mrn)
    } yield xmlPayload

  private def checkMrn(xml: NodeSeq, mrn: Option[String]): Either[ErrorWrapper[_], Unit] =
    mrn match {
      case Some(mrn) =>
        val docMRN = (xml \\ "DocNumHEA5").head.text
        if (mrn == docMRN) Right(()) else Left(ErrorWrapper(MRNMismatchError))
      case None => Right(())
    }

  private def validateSchema(payload: String, mrn: Option[String]) =
    time("Schema validation", "handleSubmission.validateSchema") {
      val schemaType =
        if (mrn.isDefined) SchemaTypeE313 else SchemaTypeE315
      val validationResult = schemaValidator.validate(schemaType, payload)

      validationResult.leftMap(ErrorWrapper(_))
    }

  private def validateRules(payload: NodeSeq, mrn: Option[String]) =
    time("Rule validation", "handleSubmission.validateRules") {
      val validationResult =
        if (mrn.isDefined) ruleValidator313.validate(payload) else ruleValidator315.validate(payload)

      validationResult.leftMap(ErrorWrapper(_))
    }
}
