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

package uk.gov.hmrc.entrydeclarationstore.nrs

import java.time.Instant

import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, User}
import uk.gov.hmrc.play.test.UnitSpec

class NRSSubmissionSpec extends UnitSpec {

  "NRSSubmission" must {
    "format to the correct JSON" in {
      val json = Json.parse("""
                              |{
                              |  "payload": "payloadBase64==",
                              |  "metadata": {
                              |    "businessId": "ics",
                              |    "notableEvent": "icsSubmission",
                              |    "payloadContentType": "application/xml",
                              |    "userSubmissionTimestamp": "2018-04-07T12:13:25.156Z",
                              |    "identityData": {
                              |      "internalId": "int-id",
                              |      "externalId": "ext-id",
                              |      "credentials": {
                              |        "providerId": "12345-credId",
                              |        "providerType": "GovernmmentGateway"
                              |      },
                              |      "confidenceLevel": 200,
                              |      "name": {
                              |        "name": "mickey",
                              |        "lastName": "mouse"
                              |      },
                              |      "dateOfBirth": "1985-01-01",
                              |      "email": "test@test.com",
                              |      "agentInformation": {
                              |        "agentCode": "TZRXXV",
                              |        "agentFriendlyName": "Bodgitt & Legget LLP",
                              |        "agentId": "BDGL"
                              |      },
                              |      "groupIdentifier": "GroupId",
                              |      "credentialRole": "User",
                              |      "mdtpInformation": {
                              |        "deviceId": "DeviceId",
                              |        "sessionId": "SessionId"
                              |      },
                              |      "itmpName": {
                              |        "givenName": "michael",
                              |        "middleName": "h",
                              |        "familyName": "mouse"
                              |      },
                              |      "itmpDateOfBirth": "1985-01-01",
                              |      "itmpAddress": {
                              |        "line1": "Line 1",
                              |        "postCode": "NW94HD",
                              |        "countryName": "United Kingdom",
                              |        "countryCode": "UK"
                              |      },
                              |      "affinityGroup": "Individual",
                              |      "credentialStrength": "strong",
                              |      "loginTimes": {
                              |        "currentLogin": "2016-11-27T09:00:00.000Z",
                              |        "previousLogin": "2016-11-01T12:00:00.000Z"
                              |      }
                              |    },
                              |    "userAuthToken": "AbCdEf123456",
                              |    "headerData": {
                              |      "Gov-Client-Public-IP": "198.51.100.0",
                              |      "Gov-Client-Public-Port": "12345"
                              |    },
                              |    "searchKeys": {
                              |      "eori": "GB123456789",
                              |      "correlationId": "qweryuiop",
                              |      "localReferenceNumber": "localref",
                              |      "movementReferenceNumber": "mrn"
                              |    }
                              |  }
                              |}""".stripMargin)

      val request = FakeRequest().withHeaders(
        Headers(
          "Gov-Client-Public-IP"   -> "198.51.100.0",
          "Gov-Client-Public-Port" -> "12345"
        ))

      val identityData = IdentityData(
        internalId      = Some("int-id"),
        externalId      = Some("ext-id"),
        agentCode       = None,
        credentials     = Some(Credentials("12345-credId", "GovernmmentGateway")),
        confidenceLevel = ConfidenceLevel.L200,
        nino            = None,
        saUtr           = None,
        name            = Some(Name(Some("mickey"), Some("mouse"))),
        dateOfBirth     = Some(LocalDate.parse("1985-01-01")),
        email           = Some("test@test.com"),
        agentInformation = AgentInformation(
          agentCode         = Some("TZRXXV"),
          agentFriendlyName = Some("Bodgitt & Legget LLP"),
          agentId           = Some("BDGL")
        ),
        groupIdentifier = Some("GroupId"),
        credentialRole  = Some(User),
        mdtpInformation = Some(MdtpInformation("DeviceId", "SessionId")),
        itmpName = ItmpName(
          Some("michael"),
          Some("h"),
          Some("mouse")
        ),
        itmpDateOfBirth = Some(LocalDate.parse("1985-01-01")),
        itmpAddress = ItmpAddress(
          line1       = Some("Line 1"),
          line2       = None,
          line3       = None,
          line4       = None,
          line5       = None,
          postCode    = Some("NW94HD"),
          countryName = Some("United Kingdom"),
          countryCode = Some("UK")
        ),
        affinityGroup      = Some(AffinityGroup.Individual),
        credentialStrength = Some("strong"),
        loginTimes = LoginTimes(
          DateTime.parse("2016-11-27T09:00:00.000Z"),
          Some(DateTime.parse("2016-11-01T12:00:00.000Z"))
        )
      )

      val searchKeys = SearchKeys("GB123456789", "qweryuiop", Some("localref"), Some("mrn"))

      val metadata = NRSMetadata(
        Instant.parse("2018-04-07T12:13:25.156Z"),
        identityData,
        "AbCdEf123456",
        request.headers,
        searchKeys)

      val submission = NRSSubmission("payloadBase64==", metadata)

      Json.toJson(submission) shouldBe json
    }
  }
}
