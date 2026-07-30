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

package uk.gov.hmrc.entrydeclarationstore.reporting

import org.scalatest.matchers.should.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.entrydeclarationstore.nrs.NRSMetadataTestData
import uk.gov.hmrc.entrydeclarationstore.utils.SubmissionUtils
import java.time.Instant

import uk.gov.hmrc.entrydeclarationstore.models.MessageType
import uk.gov.hmrc.entrydeclarationstore.models.json.{EntrySummaryDeclaration, Goods, Itinerary, Metadata, OfficeOfFirstEntry, Parties, Trader}

class SubmissionHandledSpec extends AnyWordSpec with NRSMetadataTestData {

  val now: Instant = Instant.now
  val eori = "GB1234567890"

  val failureType: FailureType = FailureType.MRNMismatchError
  val entrySummaryDeclaration = EntrySummaryDeclaration(
    "submissionId",
    None,
    Metadata("", "", "", MessageType.IE315, "", "", ""),
    None,
    Parties(None, None, Trader(None, None, None, None), None, None, None),
    Goods(1, None, None, None, None),
    Itinerary("", None, None, None, None, None, None, OfficeOfFirstEntry("", ""), None),
    None
  )

  val expectedIdentityDataJson: JsObject = Json
    .parse(
      """
        |{
        |  "internalId": "int-id",
        |  "externalId": "ext-id",
        |  "credentials": {
        |    "providerId": "12345-credId",
        |    "providerType": "GovernmmentGateway"
        |  },
        |  "confidenceLevel": 200,
        |  "dateOfBirth": "1985-01-01",
        |  "email": "test@test.com",
        |  "agentInformation": {
        |    "agentCode": "TZRXXV",
        |    "agentFriendlyName": "Bodgitt & Legget LLP",
        |    "agentId": "BDGL"
        |  },
        |  "groupIdentifier": "GroupId",
        |  "credentialRole": "User",
        |  "mdtpInformation": {
        |    "deviceId": "DeviceId",
        |    "sessionId": "SessionId"
        |  },
        |  "itmpName": {
        |    "givenName": "michael",
        |    "middleName": "h",
        |    "familyName": "mouse"
        |  },
        |  "itmpDateOfBirth": "1985-01-01",
        |  "itmpAddress": {
        |    "line1": "Line 1",
        |    "postCode": "NW94HD",
        |    "countryName": "United Kingdom",
        |    "countryCode": "UK"
        |  },
        |  "affinityGroup": "Individual",
        |  "credentialStrength": "strong",
        |  "enrolments": { "enrolments": [] },
        |  "loginTimes": {
        |    "currentLogin": "2016-11-27T09:00:00.000Z",
        |    "previousLogin": "2016-11-01T12:00:00.000Z"
        |  }
        |}
        |""".stripMargin
    )
    .as[JsObject]

  val expectedDetail: JsObject = Json.obj(
    "eori" -> eori,
    "identityData" -> expectedIdentityDataJson,
    "country" -> "United Kingdom",
    "enrolments" -> Json.obj("enrolments" -> Json.arr()),
    "parties" -> Json.obj("declarant" -> Json.obj())
  )

  val expectedFailureDetail: JsObject =
    Json.obj("failureType" -> "MRNMismatchError") ++ expectedDetail

  def checkEvents(
    submissionHandled: SubmissionHandled,
    auditType: String,
    transactionName: String,
    detail: JsObject): Unit = {

    "have the correct associated JSON event" in {
      val event = summon[EventSources[SubmissionHandled]].eventFor(now, submissionHandled)

      event shouldBe None
    }

    "have the correct associated audit event" in {
      val event = summon[EventSources[SubmissionHandled]].auditEventFor(submissionHandled).get

      event.auditType shouldBe auditType
      event.transactionName shouldBe transactionName
      event.detail shouldBe detail
    }
  }

  "SubmissionHandled" when {
    val handledDetails = SubmissionUtils.extractSubmissionHandledDetails(eori, Some(identityData), Right(entrySummaryDeclaration))

    "Success(true)" must {
      checkEvents(
        SubmissionHandled.Success(true, handledDetails),
        "SuccessfulAmendment",
        "Successful amendment",
        expectedDetail
      )
    }

    "Success(false)" must {
      checkEvents(
        SubmissionHandled.Success(false, handledDetails),
        "SuccessfulDeclaration",
        "Successful declaration",
        expectedDetail
      )
    }

    "Failure(true, FailureType)" must {
      checkEvents(
        SubmissionHandled.Failure(
          isAmendment = true,
          failureType, handledDetails),
        "UnsuccessfulAmendment",
        "Unsuccessful amendment",
        expectedFailureDetail
      )
    }

    "Failure(false, FailureType)" must {
      checkEvents(
        SubmissionHandled.Failure(
          isAmendment = false,
          failureType, handledDetails
        ),
        "UnsuccessfulDeclaration",
        "Unsuccessful declaration",
        expectedFailureDetail
      )
    }
  }
}
