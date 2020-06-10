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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.models.ReplaySubmissionIds
import uk.gov.hmrc.entrydeclarationstore.services.SubmissionReplayService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class SubmissionReplayController @Inject()(
  cc: ControllerComponents,
  service: SubmissionReplayService,
  appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends BackendController(cc) {
  def replay: Action[JsValue] = Action.async(parse.json) { implicit request =>
    val model = request.body.validate[ReplaySubmissionIds]
    if (model.isSuccess && model.get.submissionIds.length <= appConfig.replayBatchSizeLimit) {
      service.replaySubmission(model.get.submissionIds).map {
        case Right(replayResult) => Ok(Json.toJson(replayResult))
        case Left(_)             => InternalServerError
      }
    } else { Future.successful(BadRequest) }
  }
}