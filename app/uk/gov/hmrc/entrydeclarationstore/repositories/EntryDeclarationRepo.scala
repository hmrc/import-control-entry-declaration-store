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

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.commands.Command
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ReadPreference, WriteConcern}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

trait EntryDeclarationRepo {
  def save(entryDeclaration: EntryDeclarationModel)(implicit lc: LoggingContext): Future[Boolean]

  def lookupSubmissionId(eori: String, correlationId: String): Future[Option[SubmissionIdLookupResult]]

  def lookupEntryDeclaration(submissionId: String): Future[Option[JsValue]]

  def setSubmissionTime(submissionId: String, time: Instant)(implicit lc: LoggingContext): Future[Boolean]

  def lookupAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]]

  def lookupAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]]

  def lookupDeclarationRejectionEnrichment(submissionId: String): Future[Option[DeclarationRejectionEnrichment]]

  def lookupMetadata(submissionId: String)(
    implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]]

  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]

  def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean]

  def enableHousekeeping(value: Boolean): Future[Boolean]

  def getHousekeepingStatus: Future[HousekeepingStatus]

  def housekeep(now: Instant, limit: Int): Future[Int]
}

@Singleton
class EntryDeclarationRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: ReactiveMongoComponent,
  ec: ExecutionContext,
  mat: Materializer
) extends ReactiveRepository[EntryDeclarationPersisted, BSONObjectID](
      "entryDeclarationStore",
      mongo.mongoConnector.db,
      EntryDeclarationPersisted.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with EntryDeclarationRepo {

  private val expireAfterSecondsOn: Long  = 0
  private val expireAfterSecondsOff: Long = Long.MaxValue

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("submissionId", Ascending)), name = Some("submissionIdIndex"), unique = true),
    //TTL index
    Index(
      Seq("housekeepingAt" -> Ascending),
      name    = Some("housekeepingIndex"),
      options = BSONDocument("expireAfterSeconds" -> 0)),
    Index(
      Seq(("eori", Ascending), ("correlationId", Ascending)),
      name   = Some("eoriPlusCorrelationIdIndex"),
      unique = true
    )
  )

  override def save(entryDeclaration: EntryDeclarationModel)(implicit lc: LoggingContext): Future[Boolean] = {
    val entryDeclarationPersisted = EntryDeclarationPersisted.from(entryDeclaration, appConfig.defaultTtl)
    insert(entryDeclarationPersisted)
      .map(result => result.ok)
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to save entry declaration", e)
          false
      }
  }

  override def lookupSubmissionId(eori: String, correlationId: String): Future[Option[SubmissionIdLookupResult]] =
    collection
      .find(
        Json.obj("eori" -> eori, "correlationId" -> correlationId),
        Some(
          Json.obj("submissionId" -> 1, "receivedDateTime" -> 1, "housekeepingAt" -> 1, "eisSubmissionDateTime" -> 1))
      )
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map { doc =>
        SubmissionIdLookupResult(
          (doc \ "receivedDateTime").as[PersistableDateTime].toInstant.toString,
          (doc \ "housekeepingAt").as[PersistableDateTime].toInstant.toString,
          (doc \ "submissionId").as[String],
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant.toString)
        )
      })

  override def lookupEntryDeclaration(submissionId: String): Future[Option[JsValue]] =
    collection
      .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("payload" -> 1)))
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map(doc => (doc \ "payload").as[JsValue]))

  override def setSubmissionTime(submissionId: String, time: Instant)(implicit lc: LoggingContext): Future[Boolean] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(
        Json.obj("submissionId" -> submissionId),
        Json.obj("$set"         -> Json.obj("eisSubmissionDateTime" -> PersistableDateTime(time)))
      )
      .map(result => result.nModified > 0)
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to set submission time for entry declaration", e)
          false
      }

  override def lookupAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]] =
    collection
      .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("eisSubmissionDateTime" -> 1, "payload" -> 1)))
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map { doc =>
        AcceptanceEnrichment(
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant),
          (doc \ "payload").as[JsValue]
        )
      })

  override def lookupAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]] =
    collection
      .find(
        Json.obj("submissionId" -> submissionId),
        Some(
          Json.obj(
            "eisSubmissionDateTime"                          -> 1,
            "payload.parties.declarant"                      -> 1,
            "payload.parties.representative"                 -> 1,
            "payload.itinerary.officeOfFirstEntry.reference" -> 1,
            "payload.amendment.movementReferenceNumber"      -> 1,
            "payload.amendment.dateTime"                     -> 1
          ))
      )
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map { doc =>
        AmendmentRejectionEnrichment(
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant),
          (doc \ "payload").as[JsValue]
        )
      })

  override def lookupDeclarationRejectionEnrichment(
    submissionId: String): Future[Option[DeclarationRejectionEnrichment]] =
    collection
      .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("eisSubmissionDateTime" -> 1)))
      .one[JsObject](ReadPreference.primaryPreferred)
      .map(_.map { doc =>
        DeclarationRejectionEnrichment((doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant))
      })

  override def lookupMetadata(submissionId: String)(
    implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]] =
    collection
      .find(
        Json.obj("submissionId" -> submissionId),
        Some(
          Json.obj(
            "submissionId"                              -> 1,
            "eori"                                      -> 1,
            "correlationId"                             -> 1,
            "payload.metadata.messageType"              -> 1,
            "payload.itinerary.modeOfTransportAtBorder" -> 1,
            "mrn"                                       -> 1,
            "receivedDateTime"                          -> 1
          ))
      )
      .one[JsValue](ReadPreference.primaryPreferred)
      .map {
        case Some(json) =>
          json.validate[EntryDeclarationMetadataPersisted] match {
            case JsSuccess(value, _) => Right(value.toDomain)
            case JsError(errs) =>
              ContextLogger.warn(s"Unable to read metadata: $errs")
              Left(MetadataLookupError.DataFormatError)
          }
        case None =>
          ContextLogger.info(s"No metadata found")
          Left(MetadataLookupError.MetadataNotFound)
      }

  override def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, Json.obj("submissionId" -> submissionId))

  override def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, Json.obj("eori" -> eori, "correlationId" -> correlationId))

  private def setHousekeepingAt(time: Instant, query: JsObject): Future[Boolean] =
    collection
      .update(ordered = false, WriteConcern.Default)
      .one(query, Json.obj("$set" -> Json.obj("housekeepingAt" -> PersistableDateTime(time))))
      .map(result => result.n == 1)

  override def enableHousekeeping(value: Boolean): Future[Boolean] = {
    val ttlSecs = if (value) expireAfterSecondsOn else expireAfterSecondsOff

    val commandDoc = Json.obj(
      "collMod" -> "entryDeclarationStore",
      "index"   -> Json.obj("keyPattern" -> Json.obj("housekeepingAt" -> 1), "expireAfterSeconds" -> ttlSecs))

    val runner = Command.CommandWithPackRunner(JSONSerializationPack)
    runner(mongo.mongoConnector.db(), runner.rawCommand(commandDoc))
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .head
      .map { response =>
        response.as((JsPath \ "ok").read[Double]) match {
          case 1.0 =>
            for {
              oldTtl <- response.as((JsPath \ "expireAfterSeconds_old").readNullable[Double])
              newTtl <- response.as((JsPath \ "expireAfterSeconds_new").readNullable[Double])
            } yield Logger.warn(s"Change to TTL: old TTL $oldTtl, new TTL $newTtl")
            true
          case _ =>
            Logger.warn(s"Change to TTL failed. response: $response")
            false
        }
      }
  }

  override def getHousekeepingStatus: Future[HousekeepingStatus] =
    collection.indexesManager.list().map { indexes =>
      val optTtlSecs = for {
        idx <- indexes.find(_.key.map(_._1).contains("housekeepingAt"))
        // Read the expiry from JSON (rather than BSON) so that we can control widening to Long
        // (from the more strongly typed BSON values which can be either Int32 or Int64)
        value <- Json.toJson(idx.options).as((JsPath \ "expireAfterSeconds").readNullable[Long])
      } yield value

      optTtlSecs match {
        case Some(`expireAfterSecondsOn`)  => HousekeepingStatus.On
        case Some(`expireAfterSecondsOff`) => HousekeepingStatus.Off
        case Some(other) =>
          Logger.warn(
            s"Cannot get housekeeping status: expireAfterSeconds is $other (neither on: $expireAfterSecondsOn nor off: $expireAfterSecondsOff)")
          HousekeepingStatus.Unknown
        case None =>
          Logger.warn(s"Cannot get housekeeping status: expireAfterSeconds could not be determined")
          HousekeepingStatus.Unknown
      }
    }

  override def housekeep(now: Instant, limit: Int): Future[Int] = {
    val deleteBuilder = collection.delete(ordered = false)

    collection
      .find(
        selector   = Json.obj("housekeepingAt" -> Json.obj("$lte" -> PersistableDateTime(now))),
        projection = Some(Json.obj("_id" -> 1))
      )
      .cursor[JsObject]()
      .documentSource(maxDocs = limit)
      .mapAsync(1) { idDoc =>
        deleteBuilder.element(q = idDoc, limit = Some(1), collation = None)
      }
      .batch(100, List(_)) { (deletions, element) => // <<< FIXME 100 from app config
        element :: deletions
      }
      .mapAsync(1) { deletions =>
        collection
          .delete()
          .many(deletions)
          .map(_.n)
      }
      .runFold(0)(_ + _)
  }

//    collection
//      .delete()
//      .one(
//        q = Json.obj("housekeepingAt" -> Json.obj("$lte" -> PersistableDateTime(now))),
//        limit = None
//      )
//      .map(result => result.n)
}
