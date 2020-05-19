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
package uk.gov.hmrc.entrydeclarationstore.repositories

import java.time.Instant

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.models.{AcceptanceEnrichment, AmendmentRejectionEnrichment, EntryDeclarationModel, SubmissionIdLookupResult}
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class EntryDeclarationRepoISpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false", "mongodb.defaultTtl" -> defaultTtl.toString)
    .build()

  lazy val repository: EntryDeclarationRepoImpl = inject[EntryDeclarationRepoImpl]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repository.removeAll())
  }

  val defaultTtl: FiniteDuration = 60.seconds
  val correlationId313           = "correlationId313"
  val submissionId313            = "submissionId313"
  val correlationId315           = "correlationId315"
  val submissionId315            = "submissionId315"
  val messageSender              = "messageSender"
  val messageRecipient           = "messageRecipient"
  val eori                       = "eori"
  val time: Instant              = Instant.now
  val housekeepingAt: Instant    = time.plusMillis(defaultTtl.toMillis)
  val payload: JsValue           = ResourceUtils.withInputStreamFor("jsons/313SpecificFields.json")(Json.parse)

  val entryDeclaration313: EntryDeclarationModel = EntryDeclarationModel(
    correlationId313,
    submissionId313,
    eori,
    payload,
    Some("00REFNUM1234567890"),
    time
  )

  val entryDeclaration315: EntryDeclarationModel = EntryDeclarationModel(
    correlationId315,
    submissionId315,
    eori,
    payload,
    None,
    time
  )

  def lookupEntryDeclaration(submissionId: String): Option[EntryDeclarationModel] =
    await(repository.find("submissionId" -> submissionId)).headOption.map(_.toEntryDeclarationModel)

  "EntryDeclarationRepo" when {
    "saving an EntryDeclaration" when {

      "successfully saving a 313 EntryDeclaration" must {
        "return true" in {
          await(repository.save(entryDeclaration313)) shouldBe true
        }

        "store it in the database" in {
          lookupEntryDeclaration(submissionId313) shouldBe Some(entryDeclaration313)
        }
      }

      "successfully saving a 315 EntryDeclaration" must {
        "return true" in {
          await(repository.save(entryDeclaration315)) shouldBe true
        }

        "store it in the database" in {
          lookupEntryDeclaration(submissionId315) shouldBe Some(entryDeclaration315)
        }
      }

      "fail to save when unique submissionId constraint violated" in {
        val duplicate = entryDeclaration313.copy(eori = "otherEori")
        await(repository.save(duplicate)) shouldBe false
      }

      "fail to save when unique eori + correlationId constraint violated" in {
        // Keep eori and correlationId unchanged
        val duplicate = entryDeclaration313.copy(submissionId = "other")
        await(repository.save(duplicate)) shouldBe false
      }
      "housekeepingAt must be initialised to 7 days" in {
        await(repository.find("submissionId" -> submissionId313)).headOption.map(entryDeclaration =>
          entryDeclaration.housekeepingAt.$date - entryDeclaration.receivedDateTime.toEpochMilli) shouldBe Some(
          defaultTtl.toMillis)
      }

      "expireAfterSeconds" must {
        "initially be zero" in {
          await(repository.getExpireAfterSeconds).get shouldBe 0
        }
      }
    }
  }

  "looking up a submissionId from an eori & correlationId" when {
    "a document with the eori & correlationId exists in the database" must {
      "return its submissionId" in {
        await(repository.lookupSubmissionId(eori, correlationId313)) shouldBe
          Some(SubmissionIdLookupResult(time.toString, housekeepingAt.toString, submissionId313))
      }
    }

    "no document with the eori & correlationId exists in the database" must {
      "return None" in {
        await(repository.lookupSubmissionId("unknownEori", "unknownCorrelationId313")) shouldBe None
      }
    }

    // Check find uses both fields...
    "document with the same eori but different correlationId exists in the database" must {
      "return None" in {
        await(repository.lookupSubmissionId(eori, "unknownCorrelationId313")) shouldBe None
      }
    }

    "document with the different eori but same correlationId exists in the database" must {
      "return None" in {
        await(repository.lookupSubmissionId("unknown", correlationId313)) shouldBe None
      }
    }
  }

  "looking up an entry-declaration from a submissionId" when {
    "a document with the submissionId exists in the database" must {
      "return its xml submission" in {
        await(repository.lookupEntryDeclaration(submissionId313)) shouldBe Some(payload)
      }
    }

    "no document with the submissionId exists in the database" must {
      "return None" in {
        await(repository.lookupEntryDeclaration("unknownsubmissionId313")) shouldBe None
      }
    }

  }

  "setting the submission time" when {
    val time = Instant.now

    "EntryDeclaration exists" must {
      "update it and return true" in {
        await(repository.setSubmissionTime(submissionId313, time)) shouldBe true

        lookupEntryDeclaration(submissionId313) shouldBe Some(
          entryDeclaration313.copy(eisSubmissionDateTime = Some(time)))
      }
    }

    "EntryDeclaration does not exist" must {
      "return false" in {
        await(repository.setSubmissionTime("unknownsubmissionId", time)) shouldBe false
      }
    }
  }

  "looking up an acceptance enrichment" when {
    "a document with the submissionId exists in the database" must {
      "return the enrichment" in {
        await(repository.lookupAcceptanceEnrichment("submissionId313")) shouldBe
          Some(
            AcceptanceEnrichment(
              payload
            ))
      }
    }

    "no document with the submissionId exists in the database" must {
      "return None" in {
        await(repository.lookupAcceptanceEnrichment("unknownsubmissionId313")) shouldBe None
      }
    }
  }

  "looking up a rejection enrichment" when {
    "a document with the submissionId exists in the database" must {
      "return the enrichment" in {
        val expected = ResourceUtils.withInputStreamFor("jsons/AmendmentRejectionEnrichmentPayload.json")(Json.parse)

        await(repository.lookupAmendmentRejectionEnrichment("submissionId313")) shouldBe
          Some(
            AmendmentRejectionEnrichment(
              expected
            ))
      }
    }

    "no document with the submissionId exists in the database" must {
      "return None" in {
        await(repository.lookupAmendmentRejectionEnrichment("unknownsubmissionId313")) shouldBe None
      }
    }
  }
}
