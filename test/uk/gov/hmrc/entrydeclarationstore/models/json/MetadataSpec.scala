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

package uk.gov.hmrc.entrydeclarationstore.models.json

import com.lucidchart.open.xtract.ParseSuccess
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.{Inside, WordSpec}
import uk.gov.hmrc.entrydeclarationstore.models.MessageType

import java.time.Instant

class MetadataSpec extends WordSpec with Inside {

  "metadata" when {
    {
      "converting from xml" must {
        "split the sender eori and branch from the MesSenMES3 element" in {

          val xml =
            <a>
              <MesSenMES3>  eori  /branch  </MesSenMES3>
              <DatOfPreMES9>010101</DatOfPreMES9>
              <TimOfPreMES10>1245</TimOfPreMES10>
              <MesTypMES20>E315</MesTypMES20>
              <MesIdeMES19>messageID</MesIdeMES19>
            </a>

          val submissionId     = "submissionId"
          val correlationId    = "correlationId"
          val receivedDateTime = Instant.now

          inside(
            Metadata
              .reader(InputParameters(None, submissionId, correlationId, receivedDateTime))
              .read(xml)) {
            case ParseSuccess(metadata) =>
              metadata shouldBe
                Metadata(
                  "eori",
                  "branch",
                  "2001-01-01T12:45:00.000Z",
                  MessageType.IE315,
                  receivedDateTime.toString,
                  "messageID",
                  correlationId)
          }
        }
      }
    }
  }
}
