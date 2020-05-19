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

package uk.gov.hmrc.entrydeclarationstore.reporting.audit

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditHandler @Inject()(auditConnector: AuditConnector, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def audit(auditEvent: AuditEvent)(implicit hc: HeaderCarrier): Future[Unit] = {

    val eventTags = AuditExtensions.auditHeaderCarrier(hc).toAuditTags() +
      ("transactionName" -> auditEvent.transactionName)

    val extendedDataEvent = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType   = auditEvent.auditType,
      detail      = auditEvent.detail,
      tags        = eventTags
    )

    // Audit connector logs failures itself so no need to do here also
    auditConnector
      .sendExtendedEvent(extendedDataEvent)
      .map(_ => ())
  }
}
