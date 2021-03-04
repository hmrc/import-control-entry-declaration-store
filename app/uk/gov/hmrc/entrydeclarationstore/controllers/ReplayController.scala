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

import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.models.ReplayLimit
import uk.gov.hmrc.entrydeclarationstore.orchestrators.ReplayOrchestrator
import uk.gov.hmrc.entrydeclarationstore.services.SubmissionReplayService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReplayController @Inject()(
  cc: ControllerComponents,
  replayOrchestrator: ReplayOrchestrator,
  submissionReplayService: SubmissionReplayService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  val startReplay: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ReplayLimit] match {
      case JsSuccess(replayLimit, _) =>
        val (futureReplayInitResult, _) =
          replayOrchestrator.startReplay(replayLimit.value)

        futureReplayInitResult.map(result => Accepted(Json.toJson(result)))

      case JsError(_) =>
        Future.successful(BadRequest)
    }
  }

  def getUndeliveredCounts: Action[AnyContent] = Action.async { _ =>
    submissionReplayService.getUndeliveredCounts.map { counts =>
      Ok(Json.toJson(counts))
    }
  }
}
