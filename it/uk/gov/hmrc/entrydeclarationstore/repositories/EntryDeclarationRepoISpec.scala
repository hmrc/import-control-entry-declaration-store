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

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models._
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
    with Eventually
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
  val receivedDateTime: Instant  = Instant.now
  val housekeepingAt: Instant    = receivedDateTime.plusMillis(defaultTtl.toMillis)
  val payload313: JsValue        = ResourceUtils.withInputStreamFor("jsons/313SpecificFields.json")(Json.parse)
  val payload315: JsValue        = ResourceUtils.withInputStreamFor("jsons/315NoOptional.json")(Json.parse)

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  val entryDeclaration313: EntryDeclarationModel = EntryDeclarationModel(
    correlationId313,
    submissionId313,
    eori,
    payload313,
    Some("00REFNUM1234567890"),
    receivedDateTime
  )

  val entryDeclaration315: EntryDeclarationModel = EntryDeclarationModel(
    correlationId315,
    submissionId315,
    eori,
    payload315,
    None,
    receivedDateTime
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
          entryDeclaration.housekeepingAt.$date - entryDeclaration.receivedDateTime.$date) shouldBe
          Some(defaultTtl.toMillis)
      }
    }

    "looking up a submissionId from an eori & correlationId" when {
      "a document with the eori & correlationId exists in the database" must {
        "return its submissionId" in {
          await(repository.lookupSubmissionId(eori, correlationId313)) shouldBe
            Some(SubmissionIdLookupResult(receivedDateTime.toString, housekeepingAt.toString, submissionId313, None))
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
          await(repository.lookupEntryDeclaration(submissionId313)) shouldBe Some(payload313)
        }
      }

      "no document with the submissionId exists in the database" must {
        "return None" in {
          await(repository.lookupEntryDeclaration("unknownsubmissionId313")) shouldBe None
        }
      }

    }

    val eisSubmissionDateTime = Instant.now

    "setting the submission time" when {
      "EntryDeclaration exists" must {
        "update it and return true" in {
          await(repository.setSubmissionTime(submissionId313, eisSubmissionDateTime)) shouldBe true

          lookupEntryDeclaration(submissionId313) shouldBe
            Some(entryDeclaration313.copy(eisSubmissionDateTime = Some(eisSubmissionDateTime)))
        }
      }

      "EntryDeclaration does not exist" must {
        "return false" in {
          await(repository.setSubmissionTime("unknownsubmissionId", eisSubmissionDateTime)) shouldBe false
        }
      }
    }

    "looking up an acceptance enrichment" when {
      "a document with the submissionId exists in the database" must {
        "return the enrichment" in {
          await(repository.lookupAcceptanceEnrichment(submissionId313)) shouldBe
            Some(AcceptanceEnrichment(Some(eisSubmissionDateTime), payload313))
        }
        "omit the EISSubmissionDateTime if it doesn't exist" in {
          await(repository.lookupAcceptanceEnrichment(submissionId315)) shouldBe
            Some(AcceptanceEnrichment(None, payload315))
        }
      }

      "no document with the submissionId exists in the database" must {
        "return None" in {
          await(repository.lookupAcceptanceEnrichment("unknownsubmissionId313")) shouldBe None
        }
      }
    }

    "looking up a rejection enrichment" when {
      "an amendment" when {
        "a document with the submissionId exists in the database" must {
          val expected = ResourceUtils.withInputStreamFor("jsons/AmendmentRejectionEnrichmentPayload.json")(Json.parse)
          "return the enrichment" in {
            await(repository.removeAll())
            await(repository.save(entryDeclaration313))
            await(repository.save(entryDeclaration315))

            await(repository.lookupAmendmentRejectionEnrichment(submissionId313)) shouldBe
              Some(AmendmentRejectionEnrichment(None, expected))
          }
          "include the EISSubmissionDateTime if it exists" in {
            await(repository.setSubmissionTime(submissionId313, eisSubmissionDateTime))

            await(repository.lookupAmendmentRejectionEnrichment(submissionId313)) shouldBe
              Some(AmendmentRejectionEnrichment(Some(eisSubmissionDateTime), expected))
          }
        }
        "no document with the submissionId exists in the database" must {
          "return None" in {
            await(repository.lookupAmendmentRejectionEnrichment("unknownsubmissionId313")) shouldBe None
          }
        }
      }
      "an declaration" when {
        "a document with the submissionId exists in the database" must {
          "return the enrichment" in {
            await(repository.lookupDeclarationRejectionEnrichment(submissionId315)) shouldBe
              Some(DeclarationRejectionEnrichment(None))
          }
          "include the EISSubmissionDateTime if it exists" in {
            await(repository.setSubmissionTime(submissionId315, eisSubmissionDateTime))

            await(repository.lookupDeclarationRejectionEnrichment(submissionId315)) shouldBe
              Some(DeclarationRejectionEnrichment(Some(eisSubmissionDateTime)))
          }
        }
        "no document with the submissionId exists in the database" must {
          "return None" in {
            await(repository.lookupDeclarationRejectionEnrichment("unknownsubmissionId313")) shouldBe None
          }
        }
      }
    }

    "looking up metadata" when {
      "a IE313 submission with the submissionId exists in the database" must {
        "return the metadata" in {
          await(repository.lookupMetadata(submissionId313)) shouldBe
            Right(
              ReplayMetadata(
                eori          = eori,
                correlationId = correlationId313,
                metadata = EntryDeclarationMetadata(
                  submissionId            = submissionId313,
                  messageType             = MessageType.IE313,
                  modeOfTransport         = "2",
                  receivedDateTime        = receivedDateTime,
                  movementReferenceNumber = Some("00REFNUM1234567890")
                )
              ))
        }
      }

      "a IE315 submission with the submissionId exists in the database" must {
        "return the metadata" in {
          await(repository.lookupMetadata(submissionId315)) shouldBe
            Right(
              ReplayMetadata(
                eori          = eori,
                correlationId = correlationId315,
                EntryDeclarationMetadata(
                  submissionId            = submissionId315,
                  messageType             = MessageType.IE315,
                  modeOfTransport         = "2",
                  receivedDateTime        = receivedDateTime,
                  movementReferenceNumber = None
                )
              ))
        }
      }

      // Because when we are replaying we don't want a single bad document
      // to cause the whole batch to fail or the whole replay to be aborted,
      // so we need to be able to catch this scenario...
      "invalid data is stored in the database" must {
        "return a DataFormatError" in {
          val badDeclaration = entryDeclaration315.copy(
            submissionId  = "badPayloadSubmissionID",
            correlationId = "badPayloadCorrelationId",
            payload       = JsObject.empty)

          await(repository.save(badDeclaration)) shouldBe true
          await(repository.lookupMetadata("badPayloadSubmissionID")) shouldBe
            Left(MetadataLookupError.DataFormatError)
        }
      }

      "no document with the submissionId exists in the database" must {
        "return MetadataNotFound" in {
          await(repository.lookupMetadata("unknownsubmissionId313")) shouldBe
            Left(MetadataLookupError.MetadataNotFound)
        }
      }
    }

    "housekeepingAt" when {
      "searching by submissionId" must {
        val time = Instant.now.plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                shouldBe true
          await(repository.setHousekeepingAt(submissionId313, time)) shouldBe true

          await(
            repository.collection
              .find(Json.obj("submissionId" -> submissionId313), Option.empty[JsObject])
              .one[EntryDeclarationPersisted]
              .map(_.map(_.housekeepingAt.toInstant))).get shouldBe time
        }

        "return true if no change is made" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                shouldBe true
          await(repository.setHousekeepingAt(submissionId313, time)) shouldBe true
          await(repository.setHousekeepingAt(submissionId313, time)) shouldBe true
        }

        "return false if no submission exists" in {
          await(repository.setHousekeepingAt("unknownSubmissionId", time)) shouldBe false
        }
      }
      "searching by eori and correlationId" must {
        val time = Instant.now.plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                       shouldBe true
          await(repository.setHousekeepingAt(eori, correlationId313, time)) shouldBe true

          await(
            repository.collection
              .find(Json.obj("eori" -> eori, "correlationId" -> correlationId313), Option.empty[JsObject])
              .one[EntryDeclarationPersisted]
              .map(_.map(_.housekeepingAt.toInstant))).get shouldBe time
        }

        "return true if no change is made" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                       shouldBe true
          await(repository.setHousekeepingAt(eori, correlationId313, time)) shouldBe true
          await(repository.setHousekeepingAt(eori, correlationId313, time)) shouldBe true
        }

        "return false if no submission exists" in {
          await(repository.setHousekeepingAt(eori, "unknownCorrelationId", time)) shouldBe false
        }
      }
    }

    "expireAfterSeconds" must {
      "report on when on" in {
        await(repository.enableHousekeeping(true)) shouldBe true
        await(repository.getHousekeepingStatus)    shouldBe HousekeepingStatus.On
      }

      "be effective when on" in {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313)) shouldBe true
        repository.setHousekeepingAt(submissionId313, Instant.now)

        eventually(Timeout(Span(60, Seconds))) {
          await(repository.lookupEntryDeclaration(submissionId313)) shouldBe None
        }
      }

      "be updatable (to turn off housekeeping)" in {
        await(repository.enableHousekeeping(false)) shouldBe true
        await(repository.getHousekeepingStatus)     shouldBe HousekeepingStatus.Off
      }

      "allow turning off when already off" in {
        await(repository.enableHousekeeping(false)) shouldBe true
        await(repository.getHousekeepingStatus)     shouldBe HousekeepingStatus.Off
      }

      "not be effective when off" in {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313)) shouldBe true
        repository.setHousekeepingAt(submissionId313, Instant.now)

        Thread.sleep(60000)
        await(repository.lookupEntryDeclaration(submissionId313)) should not be None

      }

      "be updatable (to turn on housekeeping)" in {
        await(repository.enableHousekeeping(true)) shouldBe true
        await(repository.getHousekeepingStatus)    shouldBe HousekeepingStatus.On
      }

      "allow turning on when already on" in {
        await(repository.enableHousekeeping(true)) shouldBe true
        await(repository.getHousekeepingStatus)    shouldBe HousekeepingStatus.On
      }
    }
  }
}
