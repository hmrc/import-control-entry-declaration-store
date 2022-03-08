/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.config

import play.api.{Configuration, Logging}
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchConfig
import uk.gov.hmrc.entrydeclarationstore.utils.{Retrying, XmlFormatConfig}
import uk.gov.hmrc.play.bootstrap.config.{AppName, ServicesConfig}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

trait AppConfig {

  def authBaseUrl: String

  def xmlFormatConfig: XmlFormatConfig

  def appName: String

  def apiGatewayContext: String

  def apiEndpointsEnabled: Boolean

  def apiStatus: String

  def auditingEnabled: Boolean

  def graphiteHost: String

  def apiSubscriptionFieldsHost: String

  def eventsHost: String

  def eisHost: String

  def eisNewEnsUrlPath: String

  def eisAmendEnsUrlPath: String

  def eisBearerToken: String

  def eisInboundBearerToken: String

  def eisEnvironment: String

  def eisRetries: List[FiniteDuration]

  def eisRetryStatusCodes: Set[Int]

  def validateXMLtoJsonTransformation: Boolean

  def businessRules315: Seq[String]

  def businessRules313: Seq[String]

  def headerAllowlist: Seq[String]

  def eisTrafficSwitchConfig: TrafficSwitchConfig

  def defaultTtl: FiniteDuration

  def shortTtl: FiniteDuration

  def housekeepingBatchSize: Int

  def housekeepingRunInterval: FiniteDuration

  def housekeepingLockDuration: FiniteDuration

  def housekeepingRunLimit: Int

  def logSubmissionPayloads: Boolean

  //NRS config items
  def nrsBaseUrl: String

  def nrsApiKey: String

  def nrsRetries: List[FiniteDuration]

  def nrsEnabled: Boolean

  def replayBatchSize: Int

  def replayLockDuration: FiniteDuration

  def autoReplayLockDuration: FiniteDuration
  def autoReplayRunInterval: FiniteDuration
  def replayCountAfterTrafficSwitchReset: Int
}

@Singleton
class AppConfigImpl @Inject()(config: Configuration, servicesConfig: ServicesConfig) extends AppConfig with Logging {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  private val eisConfig = config.get[Configuration]("microservice.services.import-control-entry-declaration-eis")

  private val nrsConfig = config.get[Configuration]("microservice.services.non-repudiation")

  val xmlFormatConfig: XmlFormatConfig =
    XmlFormatConfig(config.get[Int]("response.max.errors"))

  private final def getFiniteDuration(config: Configuration, path: String): FiniteDuration = {
    val string = config.get[String](path)

    Duration.create(string) match {
      case f: FiniteDuration => f
      case _                 => throw new RuntimeException(s"Not a finite duration '$string' for $path")
    }
  }

  lazy val appName: String = AppName.fromConfiguration(config)

  lazy val apiGatewayContext: String = config.get[String]("api.gateway.context")

  lazy val apiEndpointsEnabled: Boolean = config.get[Boolean]("api.endpoints.enabled")

  lazy val apiStatus: String = config.get[String]("api.status")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")

  val graphiteHost: String = config.get[String]("microservice.metrics.graphite.host")

  val apiSubscriptionFieldsHost: String = servicesConfig.baseUrl("api-subscription-fields")

  lazy val eventsHost: String = servicesConfig.baseUrl("import-control-entry-declaration-events")

  lazy val eisHost: String = servicesConfig.baseUrl("import-control-entry-declaration-eis")

  lazy val eisNewEnsUrlPath: String = eisConfig.get[String]("new-ens-url-path")

  lazy val eisAmendEnsUrlPath: String = eisConfig.get[String]("amend-ens-url-path")

  lazy val eisEnvironment: String = eisConfig.get[String]("environment")

  lazy val eisBearerToken: String = eisConfig.get[String]("bearerToken")

  lazy val eisInboundBearerToken: String =
    config.get[String]("microservice.services.import-control-entry-declaration-eis.inboundBearerToken")

  lazy val eisRetries: List[FiniteDuration] = fibonacciRetryDelays(eisConfig)

  lazy val eisRetryStatusCodes: Set[Int] = eisConfig.get[Seq[Int]]("retryStatusCodes").toSet

  lazy val validateXMLtoJsonTransformation: Boolean =
    config.getOptional[Boolean]("validateXMLtoJsonTransformation").getOrElse(false)

  lazy val businessRules315: Seq[String] = config.get[Seq[String]]("businessRules315")

  lazy val businessRules313: Seq[String] = config.get[Seq[String]]("businessRules313")

  lazy val headerAllowlist: Seq[String] = config.get[Seq[String]]("bootstrap.http.headersAllowlist")

  lazy val eisTrafficSwitchConfig: TrafficSwitchConfig = {
    val callTimeout    = getFiniteDuration(eisConfig, "trafficSwitch.callTimeout")
    val totalRetryTime = eisRetries.fold(Duration.Zero)(_ + _)

    if (callTimeout < totalRetryTime) {
      logger.warn(s"Configured call timeout $callTimeout is less than total retry timeout $totalRetryTime")
    }

    TrafficSwitchConfig(
      maxFailures                  = eisConfig.get[Int]("trafficSwitch.maxFailures"),
      callTimeout                  = callTimeout,
      flowingStateRefreshPeriod    = getFiniteDuration(eisConfig, "trafficSwitch.flowingStateRefreshPeriod"),
      notFlowingStateRefreshPeriod = getFiniteDuration(eisConfig, "trafficSwitch.notFlowingStateRefreshPeriod")
    )
  }

  lazy val defaultTtl: FiniteDuration = getFiniteDuration(config.get[Configuration](s"mongodb"), "defaultTtl")

  lazy val shortTtl: FiniteDuration = getFiniteDuration(config.get[Configuration](s"mongodb"), "shortTtl")

  lazy val housekeepingBatchSize: Int = config.get[Configuration](s"mongodb").get[Int]("housekeepingBatchSize")

  lazy val housekeepingRunInterval: FiniteDuration =
    getFiniteDuration(config.get[Configuration](s"mongodb"), "housekeepingRunInterval")

  lazy val housekeepingLockDuration: FiniteDuration =
    getFiniteDuration(config.get[Configuration](s"mongodb"), "housekeepingLockDuration")

  lazy val housekeepingRunLimit: Int = config.get[Configuration](s"mongodb").get[Int]("housekeepingRunLimit")

  lazy val logSubmissionPayloads: Boolean = config.get[Boolean]("logSubmissionPayloads")

  lazy val nrsBaseUrl: String = servicesConfig.baseUrl("non-repudiation")

  lazy val nrsApiKey: String = nrsConfig.get[String]("xApiKey")

  lazy val nrsRetries: List[FiniteDuration] = fibonacciRetryDelays(nrsConfig)

  lazy val nrsEnabled: Boolean = nrsConfig.getOptional[Boolean]("enabled").getOrElse(true)

  lazy val replayBatchSize: Int = config.getOptional[Int]("replay.batchSize").getOrElse(10)

  lazy val replayLockDuration: FiniteDuration = getFiniteDuration(config, "replay.lockDuration")

  lazy val autoReplayLockDuration: FiniteDuration = getFiniteDuration(config, "auto-replay.lockDuration")
  lazy val autoReplayRunInterval: FiniteDuration = getFiniteDuration(config, "auto-replay.runInterval")
  lazy val replayCountAfterTrafficSwitchReset: Int = config.get[Int]("auto-replay.replayCountAfterTrafficSwitchReset")

  private def fibonacciRetryDelays(conf: Configuration): List[FiniteDuration] =
    Retrying.fibonacciDelays(getFiniteDuration(conf, "initialDelay"), conf.get[Int]("numberOfRetries"))
}
