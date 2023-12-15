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

package uk.gov.hmrc.entrydeclarationstore.validation

import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, RawPayload}
import uk.gov.hmrc.entrydeclarationstore.utils.{Timer, XmlFormatConfig}
import uk.gov.hmrc.entrydeclarationstore.validation.business.RuleValidator
import uk.gov.hmrc.entrydeclarationstore.validation.schema.{SchemaTypeE313, SchemaTypeE315, SchemaTypeE313New, SchemaTypeE315New, SchemaValidationResult, SchemaValidator}

import javax.inject.{Inject, Named}
import scala.xml.NodeSeq

trait ValidationHandler {
  def handleValidation(rawPayload: RawPayload, eori: String, mrn: Option[String])(
    implicit lc: LoggingContext): Either[ErrorWrapper[_], NodeSeq]
}

class ValidationHandlerImpl @Inject()(
  schemaValidator: SchemaValidator,
  @Named("ruleValidator313") ruleValidator313: RuleValidator,
  @Named("ruleValidator313New") ruleValidator313New: RuleValidator,
  @Named("ruleValidator315") ruleValidator315: RuleValidator,
  @Named("ruleValidator315New") ruleValidator315New: RuleValidator,
  override val metrics: Metrics,
  appConfig: AppConfig)
    extends ValidationHandler
    with Timer
    with Logging {

  implicit val xmlFormatConfig: XmlFormatConfig = appConfig.xmlFormatConfig

  def handleValidation(rawPayload: RawPayload, eori: String, mrn: Option[String])(
    implicit lc: LoggingContext): Either[ErrorWrapper[_], NodeSeq] =
    for {
      xmlPayload <- validateSchema(rawPayload, eori, mrn)
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

  private def validateSchema(rawPayload: RawPayload, eori: String, mrn: Option[String])(implicit lc: LoggingContext) =
    time("Schema validation", "handleSubmission.validateSchema") {
      def logSchemaErrors(errs: ValidationErrors): Unit =
        ContextLogger.info(s"Schema validation errors found. Num errs=${errs.errors.length}")

//      val schemaType =
//        if (mrn.isDefined) SchemaTypeE313 else SchemaTypeE315

      val schemaType = (mrn.isDefined, appConfig.optionalFieldsFeature) match {
        case (true, false) => SchemaTypeE313
        case (false, false) => SchemaTypeE315
        case (true, true) => SchemaTypeE313New
        case (false, true) => SchemaTypeE315New
      }

      val validationResult = schemaValidator.validate(schemaType, rawPayload)

      validationResult match {
        case SchemaValidationResult.Valid(xml) =>
          checkEori(eori, xml).flatMap(_ => Right(xml))

        case SchemaValidationResult.Invalid(xml, errs) =>
          checkEori(eori, xml).flatMap { _ =>
            logSchemaErrors(errs)
            Left(ErrorWrapper(errs))
          }

        case SchemaValidationResult.Malformed(errs) =>
          logSchemaErrors(errs)
          Left(ErrorWrapper(errs))
      }
    }

  private def checkEori(eori: String, xml: NodeSeq) = {
    val docEori = for {
      docSender <- (xml \\ "MesSenMES3").headOption.map(_.text)
      eori      <- docSender.split('/').headOption.map(_.trim)
    } yield eori

    if (docEori.contains(eori)) Right(()) else Left(ErrorWrapper(EORIMismatchError))
  }

  private def validateRules(payload: NodeSeq, mrn: Option[String])(implicit lc: LoggingContext) =
    time("Rule validation", "handleSubmission.validateRules") {
//      val validationResult =
//        if (mrn.isDefined) ruleValidator313.validate(payload) else ruleValidator315.validate(payload)

      val validationResult = (mrn.isDefined, appConfig.optionalFieldsFeature) match {
        case (true, false) => ruleValidator313.validate(payload)
        case (false, false) => ruleValidator315.validate(payload)
        case (true, true) => ruleValidator313New.validate(payload)
        case (false, true) => ruleValidator315New.validate(payload)
      }

      validationResult.leftMap { errs =>
        ContextLogger.info(s"Business validation errors found. Num errs=${errs.errors.length}")
        ErrorWrapper(errs)
      }
    }
}
