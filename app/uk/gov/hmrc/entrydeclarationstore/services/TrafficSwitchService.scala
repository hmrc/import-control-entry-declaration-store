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

package uk.gov.hmrc.entrydeclarationstore.services

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationstore.models.{TrafficSwitchState, TrafficSwitchStatus}
import uk.gov.hmrc.entrydeclarationstore.repositories.TrafficSwitchRepo

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TrafficSwitchService @Inject()(repo: TrafficSwitchRepo)(implicit ec: ExecutionContext) {
  def resetTrafficSwitch: Future[Unit]                    = repo.resetToDefault
  def stopTrafficFlow: Future[Unit]                       = repo.setTrafficSwitchState(TrafficSwitchState.NotFlowing)
  def startTrafficFlow: Future[Unit]                      = repo.setTrafficSwitchState(TrafficSwitchState.Flowing)
  def getTrafficSwitchStatus: Future[TrafficSwitchStatus] = repo.getTrafficSwitchStatus
  def getTrafficSwitchState: Future[TrafficSwitchState]   = getTrafficSwitchStatus.map(_.isTrafficFlowing)
}
