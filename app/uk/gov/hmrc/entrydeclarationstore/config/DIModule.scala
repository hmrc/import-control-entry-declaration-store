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

package uk.gov.hmrc.entrydeclarationstore.config

import java.time.Clock

import akka.actor.{ActorSystem, Scheduler}
import com.google.inject.{AbstractModule, Provides}
import javax.inject.Named
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.entrydeclarationstore.connectors.{EisConnector, EisConnectorImpl}
import uk.gov.hmrc.entrydeclarationstore.reporting.events.{EventConnector, EventConnectorImpl}
import uk.gov.hmrc.entrydeclarationstore.repositories.{CircuitBreakerRepo, CircuitBreakerRepoImpl, EntryDeclarationRepo, EntryDeclarationRepoImpl}
import uk.gov.hmrc.entrydeclarationstore.services.{EntryDeclarationStore, EntryDeclarationStoreImpl}
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils
import uk.gov.hmrc.entrydeclarationstore.validation.business.{Rule, RuleValidator, RuleValidatorImpl}
import uk.gov.hmrc.entrydeclarationstore.validation.{ValidationHandler, ValidationHandlerImpl}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

class DIModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[EntryDeclarationRepo]).to(classOf[EntryDeclarationRepoImpl])
    bind(classOf[CircuitBreakerRepo]).to(classOf[CircuitBreakerRepoImpl])
    bind(classOf[AppConfig]).to(classOf[AppConfigImpl]).asEagerSingleton()
    bind(classOf[EntryDeclarationStore]).to(classOf[EntryDeclarationStoreImpl])
    bind(classOf[ValidationHandler]).to(classOf[ValidationHandlerImpl])
    bind(classOf[EisConnector]).to(classOf[EisConnectorImpl])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[EventConnector]).to(classOf[EventConnectorImpl])
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
  }

  @Provides
  def akkaScheduler(actorSystem: ActorSystem): Scheduler =
    actorSystem.scheduler

  @Named("ruleValidator315")
  @Provides
  def ruleValidator315(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC315A", appConfig.businessRules315)

  @Named("ruleValidator313")
  @Provides
  def ruleValidator313(appConfig: AppConfig): RuleValidator =
    ruleValidator("/ie:CC313A", appConfig.businessRules313)

  private def ruleValidator(elementBase: String, ruleResourceNames: Seq[String]): RuleValidator = {
    val rules = ruleResourceNames.map { resource =>
      ResourceUtils.withInputStreamFor(resource)(Json.parse(_).as[Rule])
    }

    new RuleValidatorImpl(Some(elementBase), rules)
  }
}
