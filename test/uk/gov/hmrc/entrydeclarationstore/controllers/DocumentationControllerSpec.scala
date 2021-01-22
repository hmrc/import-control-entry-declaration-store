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

package uk.gov.hmrc.entrydeclarationstore.controllers

import controllers.Assets
import org.scalatest.Assertion
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HttpErrorHandler
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers, Injecting}
import play.api.{Application, Environment, Mode}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DocumentationControllerSpec extends UnitSpec with MockAppConfig with Injecting with GuiceOneAppPerSuite {
  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  val assets: Assets                 = inject[Assets]
  val errorHandler: HttpErrorHandler = inject[HttpErrorHandler]

  val documentationController =
    new DocumentationController(Helpers.stubControllerComponents(), assets, mockAppConfig, errorHandler)

  "api definition" when {
    def checkDefinition(apiStatus: String, enabled: Boolean): Assertion = {
      MockAppConfig.apiStatus returns apiStatus
      MockAppConfig.apiEndpointsEnabled returns enabled
      MockAppConfig.apiGatewayContext returns "customs/imports/declarations"

      MockAppConfig.allowListEnabled returns false

      val result = documentationController.definition()(FakeRequest())

      status(result)      shouldBe OK
      contentType(result) shouldBe Some(MimeTypes.JSON)
      val definitionJson = contentAsJson(result)

      val versions = (definitionJson \ "api" \ "versions").as[Seq[JsValue]]
      versions should have size 1
      val version = versions.head

      (version \ "status").as[String]            shouldBe apiStatus
      (version \ "endpointsEnabled").as[Boolean] shouldBe enabled
      (version \ "access").toOption              shouldBe None
    }

    "no allowlist" must {
      "include the correct status and enabled flags disabled" in {
        checkDefinition("ALPHA", enabled = false)
      }

      "include the correct status and enabled flags enabled" in {
        checkDefinition("BETA", enabled = true)
      }
    }

    "allowList defined" must {
      "include the correct applicationIds if allowlists enabled" in {
        val apiStatus = "BETA"
        val enabled   = true

        val applicationIds = Seq("app1", "app2")
        MockAppConfig.apiStatus returns apiStatus
        MockAppConfig.apiEndpointsEnabled returns enabled
        MockAppConfig.apiGatewayContext returns "customs/imports/declarations"

        MockAppConfig.allowListEnabled returns true
        MockAppConfig.allowListApplicationIds returns applicationIds

        val result = documentationController.definition()(FakeRequest())

        status(result)      shouldBe OK
        contentType(result) shouldBe Some(MimeTypes.JSON)
        val definitionJson = contentAsJson(result)

        val versions = (definitionJson \ "api" \ "versions").as[Seq[JsValue]]
        versions should have size 1
        val version = versions.head

        (version \ "status").as[String]            shouldBe apiStatus
        (version \ "endpointsEnabled").as[Boolean] shouldBe enabled

        (version \ "access" \ "type").as[String]                           shouldBe "PRIVATE"
        (version \ "access" \ "whitelistedApplicationIds").as[Seq[String]] shouldBe applicationIds
      }
    }
  }

  "rule documentation" must {
    "use 313 rules for 313 documentation" in {
      MockAppConfig.businessRules313 returns Seq("rules/p2_p71.json")

      val result: Future[Result] = documentationController.rules313("anyVersion")(FakeRequest())

      val docs = contentAsString(result)

      docs should include("4065")
      docs should include("CC313A/MesSenMES3")
      docs should not include "315"
    }

    "use 315 rules for 315 documentation" in {
      MockAppConfig.businessRules315 returns Seq("rules/p2_p71.json")

      val result: Future[Result] = documentationController.rules315("anyVersion")(FakeRequest())

      val docs = contentAsString(result)

      docs should include("4065")
      docs should include("CC315A/MesSenMES3")
      docs should not include "313"
    }

    "be in error code order" in {
      // rules files have error codes out of order here...
      MockAppConfig.businessRules313 returns Seq("rules/p24_p93.json", "rules/p2_p71.json")

      val result: Future[Result] = documentationController.rules313("anyVersion")(FakeRequest())

      val docs = contentAsString(result)

      docs                 should include("4065")
      docs                 should include("8678")
      docs.indexOf("4065") should be < docs.indexOf("8678")
    }

    "render regex escape characters verbatim (i.e. as \\n etc)" in {
      MockAppConfig.businessRules315 returns Seq("rules/p2_p71.json")

      val result: Future[Result] = documentationController.rules315("anyVersion")(FakeRequest())

      val docs = contentAsString(result)

      docs should include(raw"""[Message sender] must match pattern "[A-Z]{2}[^\n\r]{1,15}/[0-9]{10}".""")
    }
  }
}
