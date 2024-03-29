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

package uk.gov.hmrc.entrydeclarationstore.nrs

import java.time.LocalDate
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Headers
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, User}
import uk.gov.hmrc.entrydeclarationstore.models.RawPayload
import uk.gov.hmrc.entrydeclarationstore.utils.ChecksumUtils._
import java.time.Instant

trait NRSMetadataTestData {

  val nrsMetadataBody: Array[Byte]      = "payload".getBytes("UTF-8")
  val nrsMetadataRawPayload: RawPayload = RawPayload(nrsMetadataBody)

  val nrsMetadataJson: JsValue =
    Json.parse(s"""
                  |{
                  |    "businessId": "iceds",
                  |    "notableEvent": "entry-declaration",
                  |    "payloadContentType": "application/xml",
                  |    "payloadSha256Checksum":"${nrsMetadataBody.calculateSha256}",
                  |    "userSubmissionTimestamp": "2018-04-07T12:13:25.000Z",
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
                  |      "enrolments":{"enrolments":[]},
                  |      "loginTimes": {
                  |        "currentLogin": "2016-11-27T09:00:00.000Z",
                  |        "previousLogin": "2016-11-01T12:00:00.000Z"
                  |      }
                  |    },
                  |    "userAuthToken": "Bearer AbCdEf123456",
                  |    "headerData": {
                  |      "Authorization": "Bearer AbCdEf123456",
                  |      "Gov-Client-Public-IP": "198.51.100.0",
                  |      "Gov-Client-Public-Port": "12345"
                  |    },
                  |    "searchKeys": {
                  |      "submissionId": "3216783621-123873821-12332"
                  |    }
                  |}""".stripMargin)

  val identityData: IdentityData = IdentityData(
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
    itmpName = Some(
      ItmpName(
        Some("michael"),
        Some("h"),
        Some("mouse")
      )),
    itmpDateOfBirth = Some(LocalDate.parse("1985-01-01")),
    itmpAddress = Some(
      ItmpAddress(
        line1       = Some("Line 1"),
        line2       = None,
        line3       = None,
        line4       = None,
        line5       = None,
        postCode    = Some("NW94HD"),
        countryName = Some("United Kingdom"),
        countryCode = Some("UK")
      )),
    affinityGroup      = Some(AffinityGroup.Individual),
    credentialStrength = Some("strong"),
    loginTimes = LoginTimes(
      Instant.parse("2016-11-27T09:00:00.000Z"),
      Some(Instant.parse("2016-11-01T12:00:00.000Z"))
    )
  )

  val nrsMetadata: NRSMetadata = {
    val request =
      FakeRequest()
        .withHeaders(
          Headers(
            "Gov-Client-Public-IP"   -> "198.51.100.0",
            "Gov-Client-Public-Port" -> "12345",
            "Authorization"          -> "Bearer AbCdEf123456"
          ))
        .withBody(nrsMetadataBody)

    NRSMetadata(
      Instant.parse("2018-04-07T12:13:25.000Z"),
      "3216783621-123873821-12332",
      identityData,
      request,
      request.body.calculateSha256)
  }

}
