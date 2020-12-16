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

package uk.gov.hmrc.entrydeclarationstore.controllers

import controllers.Assets

import javax.inject.{Inject, Singleton}
import play.api.http.HttpErrorHandler
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.business.{Assert, Rule}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class DocumentationController @Inject()(
  cc: ControllerComponents,
  assets: Assets,
  appConfig: AppConfig,
  errorHandler: HttpErrorHandler)
    extends BackendController(cc) {

  def documentation(version: String, endpointName: String): Action[AnyContent] =
    assets.at(s"/public/api/documentation/$version", s"${endpointName.replaceAll(" ", "-")}.xml")

  def definition(): Action[AnyContent] = Action {

    val allowListAccess =
      if (appConfig.allowListEnabled) {
        s""""access": {
           |  "type": "PRIVATE",
           |  "whitelistedApplicationIds": ${Json.toJson(appConfig.allowListApplicationIds)}
           |},""".stripMargin
      } else {
        ""
      }

    Ok(Json.parse(s"""{
                     |  "scopes": [
                     |    {
                     |      "key": "write:import-control-system",
                     |      "name": "Access Import Control System",
                     |      "description": "Allow submission of entry summary declarations"
                     |    }
                     |  ],
                     |  "api": {
                     |    "name": "Safety and Security Import Declarations",
                     |    "description": "API with endpoints for submission of Entry Summary Declarations.",
                     |    "context": "${appConfig.apiGatewayContext}",
                     |    "categories": [
                     |      "CUSTOMS"
                     |    ],
                     |    "versions": [
                     |      {
                     |        "version": "1.0",
                     |        "status": "${appConfig.apiStatus}",
                     |        "endpointsEnabled": ${appConfig.apiEndpointsEnabled},
                     |        $allowListAccess
                     |        "fieldDefinitions": [
                     |          {
                     |            "name": "authenticatedEori",
                     |            "description": "What's your Economic Operator Registration and Identification (EORI) number?",
                     |            "type": "STRING",
                     |            "hint": "This is your EORI that will associate your application with you as a CSP"
                     |          }
                     |        ]
                     |      }
                     |    ]
                     |  }
                     |}""".stripMargin))
  }

  def conf(version: String, file: String): Action[AnyContent] =
    assets.at(s"/public/api/conf/$version", file)

  def rules315(version: String): Action[AnyContent] = Action {
    Ok(renderRuleDoc("/CC315A", appConfig.businessRules315)).as("text/markdown")
  }

  def rules313(version: String): Action[AnyContent] = Action {
    Ok(renderRuleDoc("/CC313A", appConfig.businessRules313)).as("text/markdown")
  }

  private def renderRuleDoc(elementBase: String, ruleResourceNames: Seq[String]): String = {

    def render(rule: Rule, assert: Assert) = {
      val context = trimTrailingSlash(s"$elementBase${rule.element}")

      val scenario = addTrailingDot(assert.localErrorMessage)

      s"""**ErrorCode: ${assert.errorCode}**
         |
         |**Context Element:** $context
         |
         |**Scenario**: $scenario
         |
         |---
         |""".stripMargin
    }

    val asserts = for {
      resource <- ruleResourceNames
      rule = ResourceUtils.withInputStreamFor(resource)(Json.parse(_).as[Rule])
      assert <- rule.asserts
    } yield (rule, assert)

    val assertsRendered = asserts.sortBy(_._2.errorCode).map((render _).tupled)

    assertsRendered.mkString("\n")
  }

  private def trimTrailingSlash(string: String): String = {
    val s = string.trim
    if (s.endsWith("/")) s.take(s.length - 1) else s
  }

  private def addTrailingDot(string: String): String = {
    val s = string.trim
    if (!s.endsWith(".")) s + "." else s
  }

}
