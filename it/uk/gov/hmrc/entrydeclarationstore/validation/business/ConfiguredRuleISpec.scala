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

import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

class ConfiguredRuleISpec extends WordSpec with Injecting with GuiceOneAppPerSuite with MustMatchers {
  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure("metrics.enabled" -> "false")
    .build()

  val appConfig: AppConfig = inject[AppConfig]

  val allAsserts: Seq[(Rule, Assert, Int)] =
    (appConfig.businessRules313 ++ appConfig.businessRules315).distinct
      .map(ResourceUtils.withInputStreamFor(_)(Json.parse(_).as[Rule]))
      .flatMap(rule => rule.asserts.zipWithIndex.map { case (assert, index) => (rule, assert, index) })

  "assertion messages" should {
    allAsserts.foreach((shouldNotHaveUnescapedCharsFor(_.errorMessage) _).tupled)
  }

  "assertion tests" should {
    allAsserts.foreach((shouldNotHaveUnescapedCharsFor(_.test) _).tupled)
  }

  "assertion local messages" should {
    allAsserts.foreach((shouldNotHaveUnescapedCharsFor(_.localErrorMessage) _).tupled)
  }

  private def shouldNotHaveUnescapedCharsFor(
    accessor: Assert => String)(rule: Rule, assert: Assert, assertIndex: Int): Unit =
    s"not have unescaped characters for rule ${rule.name} assert $assertIndex" in {
      {
        accessor(assert) must not include ("\r")
        accessor(assert) must not include ("\n")
        accessor(assert) must not include ("\t")
//        accessor(assert) should fullyMatch regex "^[\\u0000-\\u007F]*$"
      }
    }

}
