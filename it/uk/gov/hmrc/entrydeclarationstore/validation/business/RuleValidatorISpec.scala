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

package uk.gov.hmrc.entrydeclarationstore.validation.business

import com.lucidchart.open.xtract.{ParseSuccess, XmlReader}
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}
import org.scalatest.WordSpec

import scala.xml.{NodeSeq, XML}

class RuleValidatorISpec extends WordSpec with GuiceOneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  "Rule validator for 315s" must {
    val ruleValidator: RuleValidator =
      app.injector.instanceOf(BindingKey(classOf[RuleValidator]).qualifiedWith("ruleValidator315"))

    validateAll(ruleValidator, "xmls/ruleTestCases315/")
  }

  "Rule validator for 313s" must {
    val ruleValidator: RuleValidator =
      app.injector.instanceOf(BindingKey(classOf[RuleValidator]).qualifiedWith("ruleValidator313"))

    validateAll(ruleValidator, "xmls/ruleTestCases313/")
  }

  def validateAll(ruleValidator: RuleValidator, directoryName: String): Unit = {
    val xmlFiles: Seq[String] = ResourceUtils.resourceList(directoryName).filter(_.endsWith(".xml"))

    xmlFiles.foreach { testCase =>
      s"produce the error for $testCase" in {
        val testCaseLocation     = directoryName + testCase
        val resource             = ResourceUtils.url(testCaseLocation)
        val xml                  = XML.load(resource)
        val expectedResponse     = ResourceUtils.url(testCaseLocation.dropRight(3).concat("ctl"))
        val xmlResponse: NodeSeq = XML.load(expectedResponse)

        val expectedErrors = XmlReader.of[ValidationErrors].read(xmlResponse) match {
          case ParseSuccess(validationErrors) => validationErrors.errors.sorted
          case _                              => Nil
        }

        val errors = ruleValidator.validate(xml) match {
          case Left(failure) => failure.errors.sorted
          case Right(_)      => Nil
        }

        // Check codes and locations first as they are easier to read should there be a problem...
        errorCodesAndLocations(errors) shouldBe errorCodesAndLocations(expectedErrors)
        messages(errors)               shouldBe messages(expectedErrors)
      }
    }
  }

  private def errorCodesAndLocations(errs: Seq[ValidationError]) =
    errs.map(err => (err.errorLocation, err.errorNumber))

  private def messages(errs: Seq[ValidationError]) =
    errs.map(err => err.errorText)
}
