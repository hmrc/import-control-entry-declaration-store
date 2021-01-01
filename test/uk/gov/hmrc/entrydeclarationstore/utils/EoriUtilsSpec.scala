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

package uk.gov.hmrc.entrydeclarationstore.utils

import uk.gov.hmrc.play.test.UnitSpec

import scala.xml.Elem

class EoriUtilsSpec extends UnitSpec {

  def payloadXml(eoriAndBranch: String): Elem =
    // @formatter:off
  <ie:CC313A>
    <MesSenMES3>{eoriAndBranch}</MesSenMES3>
    <MesRecMES6>ABC123</MesRecMES6>
    <DatOfPreMES9>030211</DatOfPreMES9>
    <TimOfPreMES10>0123</TimOfPreMES10>
    <PriMES15>A</PriMES15>
    <MesIdeMES19>ABC123</MesIdeMES19>
    <MesTypMES20>CC313A</MesTypMES20>
    <CorIdeMES25>ABC123</CorIdeMES25>
    <HEAHEA>
    </HEAHEA>
  </ie:CC313A>
  // @formatter:on

  def payloadXmlNoMesSenMES3: Elem =
    // @formatter:off
    <ie:CC313A>
      <MesRecMES6>ABC123</MesRecMES6>
      <DatOfPreMES9>030211</DatOfPreMES9>
      <TimOfPreMES10>0123</TimOfPreMES10>
      <PriMES15>A</PriMES15>
      <MesIdeMES19>ABC123</MesIdeMES19>
      <MesTypMES20>CC313A</MesTypMES20>
      <CorIdeMES25>ABC123</CorIdeMES25>
      <HEAHEA>
      </HEAHEA>
    </ie:CC313A>
  // @formatter:on

  "getting eori from a string xml payload" must {
    "return the eori" in {
      val payload = payloadXml("ABCD1234/1234567890")
      val result  = "ABCD1234"

      EoriUtils.eoriFromXmlString(payload.toString) shouldBe result
    }

    "return the eori even if no branch" in {
      val payload = payloadXml("ABCD1234")
      val result  = "ABCD1234"

      EoriUtils.eoriFromXmlString(payload.toString) shouldBe result
    }

    "trim the eori" in {
      val payload = payloadXml("\n  ABCD1234  /1234567890\n")
      val result  = "ABCD1234"

      EoriUtils.eoriFromXmlString(payload.toString) shouldBe result
    }

    "return an empty string when eori MesSenMES3 tag empty" in {
      val payload = payloadXml("")
      val result  = ""

      EoriUtils.eoriFromXmlString(payload.toString) shouldBe result
    }

    "return an empty string when no eori MesSenMES3 tag found" in {
      val payload = payloadXmlNoMesSenMES3
      val result  = ""

      EoriUtils.eoriFromXmlString(payload.toString) shouldBe result
    }

    // So that validation is not preempted
    "return the eori even if invalid xml" in {
      EoriUtils.eoriFromXmlString("""<ie:CC313A>
        <MesSenMES3>ABCD1234/1234567890</MesSenMES3>
        <MesRecMES6>ABC123</MesRecMES6>
        <DatOfPreMES9>030211</DatOfPreMES9>
        <TimOfPreMES10>0123</TimOfPreMES10>
        <PriMES15>A</PriMES15>
        <MesIdeMES19>ABC123</MesIdeMES19>
        <MesTypMES20>CC313A</MesTypMES20>
        <CorIdeMES25>ABC123</CorIdeMES25>
        <HEAHEA>""") shouldBe "ABCD1234"
    }
  }
}
