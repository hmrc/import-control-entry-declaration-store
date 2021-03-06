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

package uk.gov.hmrc.entrydeclarationstore.repositories

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ReadPreference, WriteConcern}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait EntryDeclarationRepo {
  def save(entryDeclaration: EntryDeclarationModel)(implicit lc: LoggingContext): Future[Boolean]

  def lookupSubmissionId(eori: String, correlationId: String): Future[Option[SubmissionIdLookupResult]]

  def lookupEntryDeclaration(submissionId: String): Future[Option[JsValue]]

  def setEisSubmissionSuccess(submissionId: String, time: Instant)(implicit lc: LoggingContext): Future[Boolean]

  def setEisSubmissionFailure(submissionId: String)(implicit lc: LoggingContext): Future[Boolean]

  def lookupAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]]

  def lookupAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]]

  def lookupDeclarationRejectionEnrichment(submissionId: String): Future[Option[DeclarationRejectionEnrichment]]

  def lookupMetadata(submissionId: String)(
    implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]]

  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]

  def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean]

  def housekeep(now: Instant): Future[Int]

  def getUndeliveredCounts: Future[UndeliveredCounts]

  def totalUndeliveredMessages(receivedNoLaterThan: Instant): Future[Int]

  def getUndeliveredSubmissionIds(receivedNoLaterThan: Instant, limit: Option[Int] = None): Source[String, NotUsed]
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

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("submissionId", Ascending)), name = Some("submissionIdIndex"), unique = true),
    Index(Seq("housekeepingAt" -> Ascending), name = Some("housekeepingIndex")),
    Index(
      Seq(("eori", Ascending), ("correlationId", Ascending)),
      name   = Some("eoriPlusCorrelationIdIndex"),
      unique = true
    ),
    Index(
      Seq("eisSubmissionState" -> Ascending, "receivedDateTime" -> Ascending),
      partialFilter =
        Some(BSONDocument("eisSubmissionState" -> EisSubmissionState.mongoFormatString(EisSubmissionState.Error)))
    )
  )

  override def save(entryDeclaration: EntryDeclarationModel)(implicit lc: LoggingContext): Future[Boolean] = {
    val entryDeclarationPersisted = EntryDeclarationPersisted.from(entryDeclaration, appConfig.defaultTtl)
    Mdc
      .preservingMdc(insert(entryDeclarationPersisted))
      .map(result => result.ok)
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to save entry declaration", e)
          false
      }
  }

  override def lookupSubmissionId(eori: String, correlationId: String): Future[Option[SubmissionIdLookupResult]] =
    Mdc
      .preservingMdc(
        collection
          .find(
            Json.obj("eori" -> eori, "correlationId" -> correlationId),
            Some(
              Json.obj(
                "submissionId"          -> 1,
                "receivedDateTime"      -> 1,
                "housekeepingAt"        -> 1,
                "eisSubmissionDateTime" -> 1,
                "eisSubmissionState"    -> 1))
          )
          .one[JsObject](ReadPreference.primaryPreferred))
      .map(_.map { doc =>
        SubmissionIdLookupResult(
          (doc \ "receivedDateTime").as[PersistableDateTime].toInstant.toString,
          (doc \ "housekeepingAt").as[PersistableDateTime].toInstant.toString,
          (doc \ "submissionId").as[String],
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant.toString),
          (doc \ "eisSubmissionState").as[EisSubmissionState]
        )
      })

  override def lookupEntryDeclaration(submissionId: String): Future[Option[JsValue]] =
    Mdc
      .preservingMdc(
        collection
          .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("payload" -> 1)))
          .one[JsObject](ReadPreference.primaryPreferred))
      .map(_.map(doc => (doc \ "payload").as[JsValue]))

  override def setEisSubmissionSuccess(submissionId: String, time: Instant)(
    implicit lc: LoggingContext): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .update(ordered = false, WriteConcern.Default)
          .one(
            Json.obj("submissionId" -> submissionId),
            Json.obj(
              "$set" -> Json
                .obj(
                  "eisSubmissionDateTime" -> PersistableDateTime(time),
                  "eisSubmissionState"    -> EisSubmissionState.Sent))
          ))
      .map(result => result.nModified > 0)
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to set eis submission success for entry declaration", e)
          false
      }

  override def setEisSubmissionFailure(submissionId: String)(implicit lc: LoggingContext): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .update(ordered = false, WriteConcern.Default)
          .one(
            Json.obj("submissionId" -> submissionId),
            Json.obj("$set"         -> Json.obj("eisSubmissionState" -> EisSubmissionState.Error))
          ))
      .map { result =>
        val success = result.nModified > 0
        if (success) {
          ContextLogger.warn("Submission set to Undelivered")
        }
        success
      }
      .recover {
        case e: DatabaseException =>
          ContextLogger.error(s"Unable to set eis submission failure for entry declaration", e)
          false
      }

  override def lookupAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("eisSubmissionDateTime" -> 1, "payload" -> 1)))
          .one[JsObject](ReadPreference.primaryPreferred))
      .map(_.map { doc =>
        AcceptanceEnrichment(
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant),
          (doc \ "payload").as[JsValue]
        )
      })

  override def lookupAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .find(
            Json.obj("submissionId" -> submissionId),
            Some(Json.obj(
              "eisSubmissionDateTime"                          -> 1,
              "payload.parties.declarant"                      -> 1,
              "payload.parties.representative"                 -> 1,
              "payload.itinerary.officeOfFirstEntry.reference" -> 1,
              "payload.amendment.movementReferenceNumber"      -> 1,
              "payload.amendment.dateTime"                     -> 1
            ))
          )
          .one[JsObject](ReadPreference.primaryPreferred))
      .map(_.map { doc =>
        AmendmentRejectionEnrichment(
          (doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant),
          (doc \ "payload").as[JsValue]
        )
      })

  override def lookupDeclarationRejectionEnrichment(
    submissionId: String): Future[Option[DeclarationRejectionEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .find(Json.obj("submissionId" -> submissionId), Some(Json.obj("eisSubmissionDateTime" -> 1)))
          .one[JsObject](ReadPreference.primaryPreferred))
      .map(_.map { doc =>
        DeclarationRejectionEnrichment((doc \ "eisSubmissionDateTime").asOpt[PersistableDateTime].map(_.toInstant))
      })

  override def lookupMetadata(submissionId: String)(
    implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]] =
    Mdc
      .preservingMdc(
        collection
          .find(
            Json.obj("submissionId" -> submissionId),
            Some(Json.obj(
              "submissionId"                              -> 1,
              "eori"                                      -> 1,
              "correlationId"                             -> 1,
              "payload.metadata.messageType"              -> 1,
              "payload.itinerary.modeOfTransportAtBorder" -> 1,
              "mrn"                                       -> 1,
              "receivedDateTime"                          -> 1
            ))
          )
          .one[JsValue](ReadPreference.primaryPreferred))
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
    Mdc
      .preservingMdc(
        collection
          .update(ordered = false, WriteConcern.Default)
          .one(query, Json.obj("$set" -> Json.obj("housekeepingAt" -> PersistableDateTime(time)))))
      .map(result => result.n == 1)

  override def housekeep(now: Instant): Future[Int] = {
    val deleteBuilder = collection.delete(ordered = false)

    Mdc.preservingMdc(
      collection
        .find(
          selector   = Json.obj("housekeepingAt" -> Json.obj("$lte" -> PersistableDateTime(now))),
          projection = Some(Json.obj("_id" -> 1))
        )
        .sort(Json.obj("housekeepingAt" -> 1))
        .cursor[JsObject]()
        .documentSource(maxDocs = appConfig.housekeepingRunLimit)
        .mapAsync(1) { idDoc =>
          deleteBuilder.element(q = idDoc, limit = Some(1), collation = None)
        }
        .batch(appConfig.housekeepingBatchSize, List(_)) { (deletions, element) =>
          element :: deletions
        }
        .mapAsync(1) { deletions =>
          collection
            .delete()
            .many(deletions)
            .map(_.n)
        }
        .runFold(0)(_ + _))
  }

  override def getUndeliveredCounts: Future[UndeliveredCounts] = {
    import collection.BatchCommands.AggregationFramework
    import AggregationFramework._

    val groupTransportType = Group(Json.obj("_id" -> "$payload.itinerary.modeOfTransportAtBorder"))(
      "transportMode" -> First(JsString("$payload.itinerary.modeOfTransportAtBorder")),
      "count"         -> Sum(JsNumber(1))
    )

    val groupIntoArray = Group(Json.obj("_id" -> ""))(
      "totalCount" -> SumField("count"),
      "transportCounts" -> Push(
        Json.obj(
          "transportMode" -> "$transportMode",
          "count"         -> "$count"
        )
      )
    )

    Mdc
      .preservingMdc(
        collection
          .aggregateWith[JsObject](explain = false)(_ =>
            Match(undeliveredSubmissionsSelector(None)) -> List(groupTransportType, groupIntoArray))
          .headOption)
      .map {
        case Some(results) => results.as[UndeliveredCounts].sorted
        case None          => UndeliveredCounts(totalCount = 0, transportCounts = None)
      }
  }

  override def totalUndeliveredMessages(receivedNoLaterThan: Instant): Future[Int] =
    Mdc.preservingMdc(count(undeliveredSubmissionsSelector(Some(receivedNoLaterThan))))

  override def getUndeliveredSubmissionIds(receivedNoLaterThan: Instant, limit: Option[Int]): Source[String, NotUsed] =
    collection
      .find(
        selector = undeliveredSubmissionsSelector(Some(receivedNoLaterThan)),
        projection = Some(
          Json.obj(
            "_id"          -> 0,
            "submissionId" -> 1
          ))
      )
      .sort(Json.obj("receivedDateTime" -> -1))
      .cursor[SubmissionId]()
      .documentSource(maxDocs = limit.getOrElse(Int.MaxValue))
      .map(_.value)
      .mapMaterializedValue(_ => NotUsed)

  private def undeliveredSubmissionsSelector(optEndTime: Option[Instant]) = {
    val endTimeClause =
      optEndTime
        .map(endTime => Json.obj("receivedDateTime" -> Json.obj("$lte" -> PersistableDateTime(endTime))))
        .getOrElse(JsObject.empty)

    Json.obj(
      "eisSubmissionState" -> EisSubmissionState.Error
    ) ++ endTimeClause
  }
}
