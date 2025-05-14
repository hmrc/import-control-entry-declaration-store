/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.controllers.testonly

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.entrydeclarationstore.controllers.{AuthorisedController, EntryDeclarationSubmissionController}
import uk.gov.hmrc.entrydeclarationstore.models.json.DeclarationToJsonConverter
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSService
import uk.gov.hmrc.entrydeclarationstore.reporting.ReportSender
import uk.gov.hmrc.entrydeclarationstore.services.{AuthService, EntryDeclarationStore}
import uk.gov.hmrc.entrydeclarationstore.utils.{IdGenerator, Timer}
import uk.gov.hmrc.entrydeclarationstore.validation.ValidationHandler

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestEntryDeclarationSubmissionController @Inject()(
  cc: ControllerComponents,
  service: EntryDeclarationStore,
  idGenerator: IdGenerator,
  validationHandler: ValidationHandler,
  declarationToJsonConverter: DeclarationToJsonConverter,
  val authService: AuthService,
  nrsService: NRSService,
  reportSender: ReportSender,
  clock: Clock,
  override val metrics: MetricRegistry
)(implicit ec: ExecutionContext)
    extends AuthorisedController(cc)
    with Timer
    with Logging {

  val postSubmissionTestOnly: Action[ByteString] = new EntryDeclarationSubmissionController(cc,
    service,
    idGenerator,
    validationHandler,
    declarationToJsonConverter,
    authService,
    nrsService,
    reportSender,
    clock,
    metrics).postSubmission

}
