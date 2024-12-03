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

package uk.gov.hmrc.entrydeclarationstore.reporting.audit

import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalamock.scalatest.MockFactory
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, not}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.entrydeclarationstore.config.MockAppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuditHandlerSpec extends AnyWordSpec with MockFactory with MockAppConfig with Inside {

  val appName         = "appname"
  val auditType       = "type"
  val transactionName = "txName"

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  MockAppConfig.appName returns appName

  val auditHandler = new AuditHandler(mockAuditConnector, mockAppConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val detail: JsObject = JsObject(Seq("detail1" -> JsString("detailValue1")))

  val event: AuditEvent = AuditEvent(
    auditType       = auditType,
    transactionName = transactionName,
    detail          = detail
  )

  "AuditHandler" must {
    "audit with the correct audit event" in {

      val eventCapture: CaptureOne[ExtendedDataEvent] = CaptureOne[ExtendedDataEvent]()
      (mockAuditConnector.sendExtendedEvent(_: ExtendedDataEvent)(_: HeaderCarrier, _: ExecutionContext)).expects(capture(
        eventCapture), hc, *).returns(Future.successful(AuditResult.Success))

      auditHandler.audit(event)

      val e = eventCapture.value

      e.auditSource shouldBe appName
      e.auditType   shouldBe auditType
      e.detail      shouldBe detail

      e.tags("transactionName") shouldBe transactionName
      e.tags.get("clientIP")    should not be None
    }
  }
}
