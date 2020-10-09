/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entrydeclarationstore.validation.business

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.play.test.UnitSpec

class ConfiguredRuleISpec extends UnitSpec with Injecting with GuiceOneAppPerSuite {
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
        accessor(assert) should not include ("\r")
        accessor(assert) should not include ("\n")
        accessor(assert) should not include ("\t")
//        accessor(assert) should fullyMatch regex "^[\\u0000-\\u007F]*$"
      }
    }

}
