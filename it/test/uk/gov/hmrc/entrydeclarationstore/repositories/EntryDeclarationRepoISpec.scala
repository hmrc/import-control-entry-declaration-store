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

package uk.gov.hmrc.entrydeclarationstore.repositories

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.entrydeclarationstore.housekeeping.HousekeepingScheduler
import uk.gov.hmrc.entrydeclarationstore.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationstore.models.{TransportCount, _}
import uk.gov.hmrc.entrydeclarationstore.utils.ResourceUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

class EntryDeclarationRepoISpec
    extends AnyWordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Injecting {

  val housekeepingRunLimit: Int  = 20
  val housekeepingBatchSize: Int = 3
  val clock: Clock = Clock.tickMillis(ZoneId.systemDefault())

  override lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .disable[HousekeepingScheduler]
    .configure(
      "metrics.enabled"               -> "false",
      "mongodb.defaultTtl"            -> defaultTtl.toString,
      "mongodb.housekeepingRunLimit"  -> housekeepingRunLimit,
      "mongodb.housekeepingBatchSize" -> housekeepingBatchSize
    )
    .build()

  implicit lazy val materializer: Materializer = inject[Materializer]

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
  val receivedDateTime: Instant  = Instant.now(clock)
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

  def randomEntryDeclaration(
    receivedDateTime: Instant              = receivedDateTime,
    eisSubmissionState: EisSubmissionState = EisSubmissionState.NotSent): EntryDeclarationModel = EntryDeclarationModel(
    UUID.randomUUID.toString,
    UUID.randomUUID.toString,
    eori,
    payload315,
    None,
    receivedDateTime   = receivedDateTime,
    eisSubmissionState = eisSubmissionState
  )

  def lookupEntryDeclaration(submissionId: String): Option[EntryDeclarationModel] =
    await(repository.find(submissionId).map(_.map(_.toEntryDeclarationModel)))

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
        await(repository.find(submissionId313).map(entryDeclaration =>
          entryDeclaration.map(ed => ed.housekeepingAt.toEpochMilli - ed.receivedDateTime.toEpochMilli()))) shouldBe
          Some(defaultTtl.toMillis)
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

    val eisSubmissionDateTime = Instant.now(clock)

    "setting the eis submission as success" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313))
      }

      "EntryDeclaration exists" must {
        "update the submission time, set the state as Sent and return true" in new Scenario {
          await(repository.setEisSubmissionSuccess(submissionId313, eisSubmissionDateTime)) shouldBe true

          lookupEntryDeclaration(submissionId313) shouldBe
            Some(
              entryDeclaration313.copy(
                eisSubmissionDateTime = Some(eisSubmissionDateTime),
                eisSubmissionState    = EisSubmissionState.Sent
              ))
        }
      }

      "EntryDeclaration does not exist" must {
        "return false" in new Scenario {
          await(repository.setEisSubmissionSuccess("unknownsubmissionId", eisSubmissionDateTime)) shouldBe false
        }
      }
    }

    "setting the eis submission as failure" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313))
      }

      "EntryDeclaration exists" must {
        "update the submission time, set the state as Error and return true" in new Scenario {
          await(repository.setEisSubmissionFailure(submissionId313)) shouldBe true

          lookupEntryDeclaration(submissionId313) shouldBe
            Some(
              entryDeclaration313.copy(
                eisSubmissionDateTime = None,
                eisSubmissionState    = EisSubmissionState.Error
              ))
        }
      }

      "EntryDeclaration does not exist" must {
        "return false" in new Scenario {
          await(repository.setEisSubmissionSuccess("unknownsubmissionId", eisSubmissionDateTime)) shouldBe false
        }
      }
    }

    "looking up a submissionId from an eori & correlationId" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313))
      }

      "a document with the eori & correlationId exists in the database" must {
        "return its submissionId" in new Scenario {
          await(repository.lookupSubmissionId(eori, correlationId313)) shouldBe
            Some(
              SubmissionIdLookupResult(
                receivedDateTime.toString,
                housekeepingAt.toString,
                submissionId313,
                None,
                EisSubmissionState.NotSent))
        }

        "return the eisSubmissionDateTime and Sent status if set" in new Scenario {
          await(repository.setEisSubmissionSuccess(submissionId313, eisSubmissionDateTime))
          await(repository.lookupSubmissionId(eori, correlationId313)) shouldBe Some(
            SubmissionIdLookupResult(
              receivedDateTime.toString,
              housekeepingAt.toString,
              submissionId313,
              Some(eisSubmissionDateTime.toString),
              EisSubmissionState.Sent))
        }

        "return the Error status if set" in new Scenario {
          await(repository.setEisSubmissionFailure(submissionId313))
          await(repository.lookupSubmissionId(eori, correlationId313)) shouldBe Some(
            SubmissionIdLookupResult(
              receivedDateTime.toString,
              housekeepingAt.toString,
              submissionId313,
              None,
              EisSubmissionState.Error))
        }
      }

      "no document with the eori & correlationId exists in the database" must {
        "return None" in new Scenario {
          await(repository.lookupSubmissionId("unknownEori", "unknownCorrelationId313")) shouldBe None
        }
      }

      // Check find uses both fields...
      "document with the same eori but different correlationId exists in the database" must {
        "return None" in new Scenario {
          await(repository.lookupSubmissionId(eori, "unknownCorrelationId313")) shouldBe None
        }
      }

      "document with the different eori but same correlationId exists in the database" must {
        "return None" in new Scenario {
          await(repository.lookupSubmissionId("unknown", correlationId313)) shouldBe None
        }
      }
    }

    "looking up an acceptance enrichment" when {
      trait Scenario {
        await(repository.removeAll())
        await(repository.save(entryDeclaration313))
        await(repository.save(entryDeclaration315))
        await(repository.setEisSubmissionSuccess(submissionId313, eisSubmissionDateTime))
      }

      "a document with the submissionId exists in the database" must {
        "return the enrichment" in new Scenario {
          await(repository.lookupAcceptanceEnrichment(submissionId313)) shouldBe
            Some(AcceptanceEnrichment(Some(eisSubmissionDateTime), payload313))
        }
        "omit the EISSubmissionDateTime if it doesn't exist" in new Scenario {
          await(repository.lookupAcceptanceEnrichment(submissionId315)) shouldBe
            Some(AcceptanceEnrichment(None, payload315))
        }
      }

      "no document with the submissionId exists in the database" must {
        "return None" in new Scenario {
          await(repository.lookupAcceptanceEnrichment("unknownsubmissionId313")) shouldBe None
        }
      }
    }

    "looking up a rejection enrichment" when {
      "an amendment" when {
        trait Scenario {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))
        }

        "a document with the submissionId exists in the database" must {
          val expected = ResourceUtils.withInputStreamFor("jsons/AmendmentRejectionEnrichmentPayload.json")(Json.parse)

          "return the enrichment" in new Scenario {
            await(repository.lookupAmendmentRejectionEnrichment(submissionId313)) shouldBe
              Some(AmendmentRejectionEnrichment(None, expected))
          }
          "include the EISSubmissionDateTime if it exists" in new Scenario {
            await(repository.setEisSubmissionSuccess(submissionId313, eisSubmissionDateTime))

            await(repository.lookupAmendmentRejectionEnrichment(submissionId313)) shouldBe
              Some(AmendmentRejectionEnrichment(Some(eisSubmissionDateTime), expected))
          }
        }
        "no document with the submissionId exists in the database" must {
          "return None" in new Scenario {
            await(repository.lookupAmendmentRejectionEnrichment("unknownsubmissionId313")) shouldBe None
          }
        }
      }

      "a declaration" when {
        trait Scenario {
          await(repository.removeAll())
          await(repository.save(entryDeclaration315))
        }

        "a document with the submissionId exists in the database" must {
          "return the enrichment" in new Scenario {
            await(repository.lookupDeclarationRejectionEnrichment(submissionId315)) shouldBe
              Some(DeclarationRejectionEnrichment(None))
          }
          "include the EISSubmissionDateTime if it exists" in new Scenario {
            await(repository.setEisSubmissionSuccess(submissionId315, eisSubmissionDateTime))

            await(repository.lookupDeclarationRejectionEnrichment(submissionId315)) shouldBe
              Some(DeclarationRejectionEnrichment(Some(eisSubmissionDateTime)))
          }
        }
        "no document with the submissionId exists in the database" must {
          "return None" in new Scenario {
            await(repository.lookupDeclarationRejectionEnrichment("unknownsubmissionId313")) shouldBe None
          }
        }
      }
    }

    "looking up metadata" when {
      "a IE313 submission with the submissionId exists in the database" must {
        trait Scenario {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))
        }

        "return the metadata" in new Scenario {
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
        trait Scenario {
          await(repository.removeAll())
          await(repository.save(entryDeclaration315))
        }

        "return the metadata" in new Scenario {
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
        val time = Instant.now(clock).plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                shouldBe true
          await(repository.setHousekeepingAt(submissionId313, time)) shouldBe true

          await(
            repository
              .find(submissionId313)
              .map(_.map(_.housekeepingAt))).get shouldBe time
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
        val time = Instant.now(clock).plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(entryDeclaration313))                       shouldBe true
          await(repository.setHousekeepingAt(eori, correlationId313, time)) shouldBe true

          await(
            repository
              .find(eori, correlationId313)
              .map(_.map(_.housekeepingAt))).get shouldBe time
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

    "housekeep" when {
      val t0 = Instant.now(clock)

      def populateDeclarations(numDecls: Int): Seq[EntryDeclarationModel] = {
        await(repository.removeAll())
        (1 to numDecls).map { _ =>
          val decl = randomEntryDeclaration()
          await(repository.save(decl)) shouldBe true
          decl
        }
      }

      def setHousekeepingAt(decls: Seq[EntryDeclarationModel], housekeepingTimes: Seq[Int]): Unit =
        (decls zip housekeepingTimes).foreach {
          case (decl, i) =>
            await(repository.setHousekeepingAt(decl.submissionId, t0.plusSeconds(i))) shouldBe true
        }

      def assertNotHousekept(decl: EntryDeclarationModel): Assertion =
        await(repository.lookupEntryDeclaration(decl.submissionId)) should not be None

      def assertHousekept(decl: EntryDeclarationModel): Assertion =
        await(repository.lookupEntryDeclaration(decl.submissionId)) shouldBe None

      "the time has reached the housekeepingAt for some documents" must {
        "delete only those documents" in {
          val numDecls = 10
          val decls    = populateDeclarations(numDecls)
          setHousekeepingAt(decls, 1 to decls.size)

          val elapsedSecs = 6

          await(repository.housekeep(t0.plusSeconds(elapsedSecs))) shouldBe elapsedSecs

          decls.take(elapsedSecs) foreach assertHousekept
          decls.drop(elapsedSecs) foreach assertNotHousekept
        }
      }

      "the time has not reached the housekeepingAt for any documents" must {
        "delete nothing" in {
          val numDecls = 10
          val decls    = populateDeclarations(numDecls)
          setHousekeepingAt(decls, 1 to decls.size)

          await(repository.housekeep(t0)) shouldBe 0

          decls foreach assertNotHousekept
        }
      }

      "more records than the limit require deleting" must {
        "delete only the oldest ones by housekeepingAt even if not created in that order" in {
          val numDecls = housekeepingRunLimit * 2
          val decls    = Random.shuffle(populateDeclarations(numDecls))

          setHousekeepingAt(decls, 1 to decls.size)

          val elapsedSecs = numDecls

          await(repository.housekeep(t0.plusSeconds(elapsedSecs))) shouldBe housekeepingRunLimit

          decls.take(housekeepingRunLimit) foreach assertHousekept
          decls.drop(housekeepingRunLimit) foreach assertNotHousekept
        }
      }
    }

    "there are submissions in error (undelivered)" must {

      trait Scenario {
        await(repository.removeAll())

        def createSubmissions(number: Int, eisSubmissionState: EisSubmissionState): Seq[EntryDeclarationModel] =
          (0 until number).map { i =>
            val declaration =
              randomEntryDeclaration(receivedDateTime.minusSeconds(i), eisSubmissionState)
            await(repository.save(declaration))
            declaration
          }

        val numErrorDeclarations = 5
        val errorDeclarationIdsNewestFirst: Seq[String] =
          createSubmissions(numErrorDeclarations, EisSubmissionState.Error).map(_.submissionId)

        // Queries should ignore these...
        createSubmissions(5, EisSubmissionState.NotSent)
        createSubmissions(5, EisSubmissionState.Sent)
      }

      "count submission in error" in new Scenario {
        await(repository.totalUndeliveredMessages(receivedNoLaterThan = receivedDateTime)) shouldBe numErrorDeclarations
      }

      "not count later submission in error than the cut-off" in new Scenario {
        val numExclude = 2
        await(repository.totalUndeliveredMessages(receivedNoLaterThan = receivedDateTime.minusSeconds(numExclude))) shouldBe numErrorDeclarations - numExclude
      }

      "get the submissionIds" in new Scenario {
        await(
          repository
            .getUndeliveredSubmissionIds(receivedNoLaterThan = receivedDateTime)
            .runWith(Sink.seq)) shouldBe errorDeclarationIdsNewestFirst
      }

      "not include later submissionIds than the cut-off" in new Scenario {
        val numExclude = 2
        await(
          repository
            .getUndeliveredSubmissionIds(receivedNoLaterThan = receivedDateTime.minusSeconds(numExclude))
            .runWith(Sink.seq)) shouldBe errorDeclarationIdsNewestFirst.drop(numExclude)
      }

      "allow limiting the number of submissionIds (starting at the most recent)" in new Scenario {
        val limit = 8
        await(
          repository
            .getUndeliveredSubmissionIds(receivedNoLaterThan = receivedDateTime, limit = Some(limit))
            .runWith(Sink.seq)) shouldBe errorDeclarationIdsNewestFirst.take(limit)
      }

      "allow geting total counts of submissionIds by transport mode" in new Scenario {
        await(repository.getUndeliveredCounts) shouldBe
          UndeliveredCounts(numErrorDeclarations, Some(Seq(TransportCount("2", numErrorDeclarations))))
      }

      "allow geting total counts of submissionIds by transport mode when no undelivered" in new Scenario {
        await(repository.removeAll())

        await(repository.getUndeliveredCounts) shouldBe UndeliveredCounts(totalCount = 0, transportCounts = None)
      }

      "allow geting total counts of submissionIds by transport mode where multiple transport modes" in new Scenario {
        await(repository.updateModeOfTransport(errorDeclarationIdsNewestFirst.head, "10"))

        val result: UndeliveredCounts = await(repository.getUndeliveredCounts)

        result shouldBe
          UndeliveredCounts(
            numErrorDeclarations,
            Some(
              Seq(
                TransportCount("10", 1),
                TransportCount("2", numErrorDeclarations - 1)
              )))
      }
    }
  }
}
