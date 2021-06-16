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
import org.scalatest.{Inside, WordSpec}
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationError, ValidationErrors}

class ValidatorVulnerabilitiesSpec extends WordSpec with Inside {

  val validator                       = new SchemaValidator()
  val schemaType: SchemaTypeE313.type = SchemaTypeE313

  val error: Option[ValidationErrors] = Some(
    ValidationErrors(
      Seq(ValidationError("""//apache.org/xml/features/disallow-doctype-decl" set to true.""", "schema", "4999", "/"))))

  private def validate(xml: String): Option[ValidationErrors] =
    validator.validate(schemaType, RawPayload(xml)) match {
      case SchemaValidationResult.Invalid(_, errs) => Some(errs)
      case SchemaValidationResult.Malformed(errs)  => Some(errs)
      case SchemaValidationResult.Valid(_)         => None
    }

  "Prevent DTD attacks" when {
    "I have an XXE XML payload" in {
      val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
        "<!DOCTYPE foo [" +
        "<!ELEMENT foo ANY >" +
        "<!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]><foo>&xxe;</foo>"

      validate(xml) shouldBe error
    }
    "I have a Classic XXE Attack XML payload" in {
      val xml =
        "<?xml version=\"1.0\"?>" +
          "<!DOCTYPE data [" +
          "<!ELEMENT data (#ANY)>" +
          "<!ENTITY file SYSTEM \"file:///sys/power/image_size\">" +
          "]>" +
          "<data>&file;</data>"

      validate(xml) shouldBe error
    }
    "I have a XXE Attack using netdoc XML payload" in {
      val xml =
        "<?xml version=\"1.0\"?>" +
          "<!DOCTYPE data [" +
          "<!ELEMENT data (#PCDATA)>" +
          "<!ENTITY file SYSTEM \"netdoc:/sys/power/image_size\">" +
          "]>" +
          "<data>&file;</data>"

      validate(xml) shouldBe error
    }
    "I have a XXE Attack using UTF-16 XML payload" in {
      val xml =
        "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" +
          "<!DOCTYPE data [" +
          "<!ELEMENT data (#PCDATA)>" +
          "<!ENTITY file SYSTEM \"file:///sys/power/image_size\">" +
          "]>" +
          "<data>&file;</data>"

      validate(xml) shouldBe error
    }
    "I have a XXE Attack using UTF-7 XML payload" in {
      val xml =
        "<?xml version=\"1.0\" encoding=\"UTF-7\" ?>" +
          "<!DOCTYPE data [" +
          "<!ELEMENT data (#PCDATA)>" +
          "<!ENTITY file SYSTEM \"file:///sys/power/image_size\">" +
          "]>" +
          "<data>&file;</data>"

      validate(xml) shouldBe error
    }
    "I have a testing for entity support XML payload" in {
      val xml =
        "<!DOCTYPE data [" +
          "<!ELEMENT data (#ANY)>" +
          "<!ENTITY a0 \"dos\" >" +
          "<!ENTITY a1 \"&a0;&a0;&a0;&a0;&a0;\">" +
          "<!ENTITY a2 \"&a1;&a1;&a1;&a1;&a1;\">" +
          "]>" +
          "<data>&a2;</data>"

      validate(xml) shouldBe error
    }
    "I have a billion laughs attack XML payload" in {
      val xml =
        "<!DOCTYPE data [" +
          "<!ENTITY a0 \"dos\" >" +
          "<!ENTITY a1 \"&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;\">" +
          "<!ENTITY a2 \"&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;\">" +
          "<!ENTITY a3 \"&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;\">" +
          "<!ENTITY a4 \"&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;\">" +
          "]>" +
          "<data>&a4;</data>"

      validate(xml) shouldBe error
    }
    "I have a billion laughs parameter entities attack XML payload" in {
      val xml =
        "<!DOCTYPE data SYSTEM \"http://127.0.0.1:5000/dos_indirections_parameterEntity_wfc.dtd\" [" +
          "<!ELEMENT data (#PCDATA)>" +
          "]>" +
          "<data>&g;</data>"

      validate(xml) shouldBe error
    }
    "I have a quadratic blowup attack XML payload" in {
      val xml =
        "<!DOCTYPE data [" +
          "<!ENTITY a0 \"dosdosdosdosdosdos...dos\">" +
          "]>" +
          "<data>&a0;&a0;...&a0;</data>"

      validate(xml) shouldBe error
    }
    "I have a recursive general entities attack XML payload" in {
      val xml =
        "<!DOCTYPE data [" +
          "<!ENTITY a \"a&b;\" >" +
          "<!ENTITY b \"&a;\" >" +
          "]>" +
          "<data>&a;</data>"

      validate(xml) shouldBe error
    }
    "I have a external general entities attack XML payload" in {
      val xml =
        "<?xml version='1.0'?>" +
          "<!DOCTYPE data [" +
          "<!ENTITY dos SYSTEM \"file:///publicServer.com/largeFile.xml\" >" +
          "]>" +
          "<data>&dos;</data>"

      validate(xml) shouldBe error
    }
  }
}
