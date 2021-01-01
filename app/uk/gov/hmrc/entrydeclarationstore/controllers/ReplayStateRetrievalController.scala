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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.services.ReplayStateRetrievalService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ReplayStateRetrievalController @Inject()(
  cc: ControllerComponents,
  service: ReplayStateRetrievalService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def retrieveReplayState(replayId: String): Action[AnyContent] = Action.async { _ =>
    service.retrieveReplayState(replayId).map {
      case Some(state) => Ok(Json.toJson(state))
      case None        => NotFound
    }
  }
}
