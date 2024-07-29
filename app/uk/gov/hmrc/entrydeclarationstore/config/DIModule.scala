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

package uk.gov.hmrc.entrydeclarationstore.config

import com.google.inject.{AbstractModule, Provides}
import org.apache.pekko.actor.{ActorSystem, Scheduler}
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.entrydeclarationstore.autoreplay.{AutoReplayScheduler, AutoReplayer}
import uk.gov.hmrc.entrydeclarationstore.connectors.{EisConnector, EisConnectorImpl}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.{Housekeeper, HousekeepingScheduler}
import uk.gov.hmrc.entrydeclarationstore.nrs.{NRSConnector, NRSConnectorImpl}
import uk.gov.hmrc.entrydeclarationstore.orchestrators.{ReplayLock, ReplayLockImpl}
import uk.gov.hmrc.entrydeclarationstore.reporting.events.{EventConnector, EventConnectorImpl}
import uk.gov.hmrc.entrydeclarationstore.repositories._
import uk.gov.hmrc.entrydeclarationstore.services.{AutoReplayService, EntryDeclarationStore, EntryDeclarationStoreImpl, HousekeepingService}
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.{TrafficSwitchActor, TrafficSwitchConfig}
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.business.{Rule, RuleValidator, RuleValidatorImpl}
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationHandler, ValidationHandlerImpl}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

import java.time.Clock
import javax.inject.Named

class DIModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[EntryDeclarationRepo]).to(classOf[EntryDeclarationRepoImpl])
    bind(classOf[HousekeepingScheduler]).asEagerSingleton()
    bind(classOf[HousekeepingRepo]).to(classOf[HousekeepingRepoImpl])
    bind(classOf[AutoReplayScheduler]).asEagerSingleton()
    bind(classOf[AutoReplayer]).to(classOf[AutoReplayService])
    bind(classOf[AutoReplayRepository]).to(classOf[AutoReplayRepositoryImpl])
    bind(classOf[Housekeeper]).to(classOf[HousekeepingService])
    bind(classOf[TrafficSwitchRepo]).to(classOf[TrafficSwitchRepoImpl])
    bind(classOf[ReplayStateRepo]).to(classOf[ReplayStateRepoImpl])
    bind(classOf[AppConfig]).to(classOf[AppConfigImpl]).asEagerSingleton()
    bind(classOf[EntryDeclarationStore]).to(classOf[EntryDeclarationStoreImpl])
    bind(classOf[ValidationHandler]).to(classOf[ValidationHandlerImpl])
    bind(classOf[EisConnector]).to(classOf[EisConnectorImpl])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[EventConnector]).to(classOf[EventConnectorImpl])
    bind(classOf[NRSConnector]).to(classOf[NRSConnectorImpl])
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    bind(classOf[TrafficSwitchActor.Factory]).to(classOf[TrafficSwitchActor.FactoryImpl])
    bind(classOf[ReplayLock]).to(classOf[ReplayLockImpl])
  }

  @Provides
  def pekkoScheduler(actorSystem: ActorSystem): Scheduler =
    actorSystem.scheduler

  @Provides
  def eisTrafficSwitchConfig(appConfig: AppConfig): TrafficSwitchConfig =
    appConfig.eisTrafficSwitchConfig

  @Named("ruleValidator315")
  @Provides
  def ruleValidator315(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC315A", appConfig.businessRules315)

  @Named("ruleValidator315New")
  @Provides
  def ruleValidator315New(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC315A", appConfig.businessRules315New)


  @Named("ruleValidator313")
  @Provides
  def ruleValidator313(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC313A", appConfig.businessRules313)

  @Named("ruleValidator313New")
  @Provides
  def ruleValidator313New(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC313A", appConfig.businessRules313New)

  private def ruleValidator(elementBase: String, ruleResourceNames: Seq[String]): RuleValidator = {
    val rules = ruleResourceNames.map { resource =>
      ResourceUtils.withInputStreamFor(resource)(Json.parse(_).as[Rule])
    }

    new RuleValidatorImpl(Some(elementBase), rules)
  }
}
