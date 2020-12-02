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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.models.ReplayLimit
import uk.gov.hmrc.entrydeclarationstore.orchestrators.ReplayOrchestrator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReplayController @Inject()(
  cc: ControllerComponents,
  service: ReplayOrchestrator
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  val startReplay: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ReplayLimit] match {
      case JsSuccess(replayLimit, _) =>
        val (futureReplayId, _) = service
          .startReplay(replayLimit.value)

        futureReplayId.map { replayId =>
          Accepted(Json.parse(s"""{"replayId": "$replayId"}"""))
        }

      case JsError(_) =>
        Future.successful(BadRequest)
    }
  }
}
