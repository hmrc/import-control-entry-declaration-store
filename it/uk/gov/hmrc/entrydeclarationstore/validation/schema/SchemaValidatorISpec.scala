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

package uk.gov.hmrc.entrydeclarationstore.validation.schema

import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import org.scalatest.WordSpec

import scala.xml.{NodeSeq, XML}

class SchemaValidatorISpec extends WordSpec with GuiceOneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  val schemaValidator: SchemaValidator = app.injector.instanceOf[SchemaValidator]

  "SchemaValidator for 315s" must {
    validateAll(SchemaTypeE315, "xmls/schemaTestCases315/")
  }

  "SchemaValidator for 313s" must {
    validateAll(SchemaTypeE313, "xmls/schemaTestCases313/")
  }

  def validateAll(schemeType: SchemaType, directoryName: String): Unit = {
    val xmlFiles: Seq[String] = ResourceUtils.resourceList(directoryName).filter(_.endsWith(".xml"))

    xmlFiles.foreach { testCase =>
      s"produce the error for $testCase" in {
        val testCaseLocation     = directoryName + testCase
        val resource             = ResourceUtils.asByteArray(testCaseLocation)
        val expectedResponse     = ResourceUtils.url(testCaseLocation.dropRight(3).concat("ctl"))
        val xmlResponse: NodeSeq = XML.load(expectedResponse)

        val expectedErrorTexts = (xmlResponse \ "Error" \ "Text").map(_.text)

        val errorTexts = schemaValidator.validate(schemeType, RawPayload(resource)) match {
          case SchemaValidationResult.Invalid(_, failure) => failure.errors.map(_.errorText)
          case SchemaValidationResult.Malformed(failure)  => failure.errors.map(_.errorText)
          case SchemaValidationResult.Valid(_)            => Nil
        }

        // Error texts in test packs do not match those in the SchemaErrorMessages-v11-1.pdf file
        // so check for now just that we get an error when we should...
        withClue(s"Got $errorTexts expected $expectedErrorTexts")(
          errorTexts.isEmpty shouldBe expectedErrorTexts.isEmpty)
      }
    }
  }
}
