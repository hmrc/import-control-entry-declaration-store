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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.entrydeclarationstore.models.{ErrorWrapper, StandardError}
import uk.gov.hmrc.entrydeclarationstore.reporting.ClientType
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, EntryDeclarationStore, MRNMismatchError}
import uk.gov.hmrc.entrydeclarationstore.utils.{EoriUtils, EventLogger, Timer}
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationErrors
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

@Singleton()
class EntryDeclarationSubmissionController @Inject()(
  cc: ControllerComponents,
  service: EntryDeclarationStore,
  authService: AuthService,
  override val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Timer
    with EventLogger {

  def xmlSuccessResponse(correlationId: String): Elem =
    // @formatter:off
    <ns:SuccessResponse TestInLive="false"
                        xmlns:ns="http://www.hmrc.gov.uk/successresponse/2"
                        xmlns:xd="http://www.w3.org/2000/09/xmldsig#">
      <ns:ResponseData>
        <CorrelationId>{correlationId}</CorrelationId>
      </ns:ResponseData>
    </ns:SuccessResponse>
  // @formatter:on

  val postSubmission: Action[String] = Action.async(parse.tolerantText) { implicit request =>
    handleSubmission(None)
  }

  def putAmendment(mrn: String): Action[String] = Action.async(parse.tolerantText) { implicit request =>
    handleSubmission(Some(mrn))
  }

  private def handleSubmission(mrn: Option[String])(implicit request: Request[String]) =
    authenticate.flatMap {
      case Right((eori, clientType)) =>
        service.handleSubmission(eori, request.body, mrn, clientType).map {
          case Left(failure @ ErrorWrapper(err)) =>
            err match {
              case _: ValidationErrors => BadRequest(failure.toXml)
              case MRNMismatchError    => BadRequest(failure.toXml)
              case _                   => InternalServerError(failure.toXml)
            }
          case Right(success) => Ok(xmlSuccessResponse(success.correlationId))
        }
      case Left(failure @ ErrorWrapper(err)) => Future.successful(Status(err.status)(failure.toXml))
    }

  private def authenticate(
    implicit request: Request[String]): Future[Either[ErrorWrapper[StandardError], (String, ClientType)]] =
    timeFuture("Handle authentication", "handleSubmissionController.authentication") {
      authService.authenticate().map {
        case Some((eori, clientType)) =>
          val eoriInXML = EoriUtils.eoriFromXmlString(request.body)
          if (eori == eoriInXML) Right((eori, clientType)) else Left(ErrorWrapper(StandardError.forbidden))
        case None => Left(ErrorWrapper(StandardError.unauthorized))
      }
    }

}
