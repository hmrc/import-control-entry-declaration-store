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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.json._
import play.api.Logger
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import org.bson.BsonValue
import org.mongodb.scala._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import org.mongodb.scala.result._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.BsonDocument
import uk.gov.hmrc.entrydeclarationstore.config.AppConfig
import uk.gov.hmrc.entrydeclarationstore.logging.{ContextLogger, LoggingContext}
import uk.gov.hmrc.entrydeclarationstore.models._
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
  def lookupMetadata(submissionId: String)(implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]]
  def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean]
  def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean]
  def housekeep(now: Instant): Future[Int]
  def getUndeliveredCounts: Future[UndeliveredCounts]
  def totalUndeliveredMessages(receivedNoLaterThan: Instant): Future[Int]
  def getUndeliveredSubmissionIds(receivedNoLaterThan: Instant, limit: Option[Int] = None): Source[String, NotUsed]
}

@Singleton
class EntryDeclarationRepoImpl @Inject()(appConfig: AppConfig)(
  implicit mongo: MongoComponent,
  ec: ExecutionContext,
  mat: Materializer
) extends PlayMongoRepository[EntryDeclarationPersisted](
  collectionName = "entryDeclarationStore",
  mongoComponent = mongo,
  domainFormat = EntryDeclarationPersisted.format,
  indexes = Seq(IndexModel(ascending("submissionId"),
                           IndexOptions()
                            .name("submissionIdIndex")
                            .unique(true)),
                IndexModel(ascending("housekeepingAt"),
                           IndexOptions()
                            .name("housekeepingIndex")),
                IndexModel(ascending("eori", "correlationId"),
                           IndexOptions()
                            .name("eoriPlusCorrelationIdIndex")
                            .unique(true)
                            .background(true)),
                IndexModel(ascending("eisSubmissionState", "receivedDateTime"),
                           IndexOptions()
                            .name("eisSubmissionState_1_receivedDateTime_1")
                            .partialFilterExpression(equal("eisSubmissionState", EisSubmissionState.mongoFormatString(EisSubmissionState.Error))))
                ),
  extraCodecs = Seq(Codecs.playFormatCodec(MongoFormats.objectIdFormat),
                    Codecs.playFormatCodec(EisSubmissionState.jsonFormat)),
  replaceIndexes = true ) with EntryDeclarationRepo {
  import EisSubmissionState.mongoFormatString

  val logger: Logger = Logger(getClass)

  //
  // Test support FNs
  //
  def removeAll(): Future[Unit] =
    collection
      .deleteMany(exists("_id"))
      .toFutureOption()
      .map( _ => ())

  def find(submissionId: String): Future[Option[EntryDeclarationPersisted]] =
    collection
      .find(equal("submissionId", submissionId))
      .headOption()

  def find(eori: String, correlationId: String): Future[Option[EntryDeclarationPersisted]] =
    collection
      .find(and(equal("eori", eori), equal("correlationId", correlationId)))
      .headOption()

  def updateModeOfTransport(submissionId: String, modeOfTransport: String): Future[Unit] =
    collection
      .updateOne(equal("submissionId", submissionId), set("payload.itinerary.modeOfTransportAtBorder", modeOfTransport))
      .toFutureOption()
      .map(_ => ())
  //
  // End of Test support FNs
  //

  override def save(entryDeclaration: EntryDeclarationModel)(implicit lc: LoggingContext): Future[Boolean] = {
    val entryDeclarationPersisted = EntryDeclarationPersisted.from(entryDeclaration, appConfig.defaultTtl)
    Mdc
      .preservingMdc(
        collection
          .insertOne(entryDeclarationPersisted)
          .toFutureOption()
      )
      .map(_.exists(_.wasAcknowledged))
      .recover {
        case e =>
          ContextLogger.error(s"Unable to save entry declaration", e)
          false
      }
  }

  override def lookupSubmissionId(eori: String, correlationId: String): Future[Option[SubmissionIdLookupResult]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find[BsonValue](and(equal("eori", eori), equal("correlationId", correlationId)))
          .projection(fields(include("submissionId", "receivedDateTime", "housekeepingAt", "eisSubmissionDateTime", "eisSubmissionState"), excludeId()))
          .headOption()
      ).map{
        case None => None
        case Some(bson) =>
          val sqr: SubmissionIdQueryResult = Codecs.fromBson[SubmissionIdQueryResult](bson)
          Some(SubmissionIdLookupResult(
            sqr.receivedDateTime.toString,
            sqr.housekeepingAt.toString,
            sqr.submissionId.toString,
            sqr.eisSubmissionDateTime.map(_.toString),
            sqr.eisSubmissionState
          ))
      }

  def lookupEntryDeclaration(submissionId: String): Future[Option[JsValue]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find[BsonValue](equal("submissionId", submissionId))
          .projection(fields(include("payload"), excludeId()))
          .headOption()
      ).map(_.map(Codecs.fromBson[EntryDeclarationPayload](_).payload))

  override def setEisSubmissionSuccess(submissionId: String, time: Instant)(implicit lc: LoggingContext): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(
            equal("submissionId", submissionId),
            combine(
              set("eisSubmissionDateTime", time),
              set("eisSubmissionState", mongoFormatString(EisSubmissionState.Sent))
            )
          )
          .toFutureOption()

      )
      .map{
        case Some(result: UpdateResult) => result.getModifiedCount > 0
        case _ => false
      }.recover {
        case e =>
          ContextLogger.error(s"Unable to set eis submission success for entry declaration", e)
          false
      }


  override def setEisSubmissionFailure(submissionId: String)(implicit lc: LoggingContext): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(
            equal("submissionId", submissionId),
            set("eisSubmissionState", mongoFormatString(EisSubmissionState.Error))
          )
          .toFutureOption()
      )
      .map {
        case Some(result: UpdateResult) if result.getModifiedCount > 0 =>
          ContextLogger.warn("Submission set to Undelivered")
          true
        case _ => false
      }
      .recover {
        case e =>
          ContextLogger.error(s"Unable to set eis submission failure for entry declaration", e)
          false
      }

  override def lookupAcceptanceEnrichment(submissionId: String): Future[Option[AcceptanceEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find[BsonValue](equal("submissionId", submissionId))
          .projection(fields(include("eisSubmissionDateTime", "payload"), excludeId()))
          .headOption()
      )
      .map(_.map{bson =>
          val result = Codecs.fromBson[EntryDeclarationPayload](bson)
          AcceptanceEnrichment(result.eisSubmissionDateTime, result.payload)
      })

  override def lookupAmendmentRejectionEnrichment(submissionId: String): Future[Option[AmendmentRejectionEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find[BsonValue](equal("submissionId", submissionId))
          .projection(fields(include("eisSubmissionDateTime",
                                     "payload.parties.declarant",
                                     "payload.parties.representative",
                                     "payload.itinerary.officeOfFirstEntry.reference",
                                     "payload.amendment.movementReferenceNumber",
                                     "payload.amendment.dateTime"), excludeId()))
          .headOption()
      )
      .map(_.map{bson =>
          val result = Codecs.fromBson[EntryDeclarationPayload](bson)
          AmendmentRejectionEnrichment(result.eisSubmissionDateTime, result.payload)
      })

  override def lookupDeclarationRejectionEnrichment(
    submissionId: String): Future[Option[DeclarationRejectionEnrichment]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find[BsonValue](equal("submissionId", submissionId))
          .projection(fields(include("eisSubmissionDateTime"), excludeId()))
          .headOption()
      )
      .map( _.map{bson =>
          val drer: DeclarationRejectionEnrichmenResult = Codecs.fromBson[DeclarationRejectionEnrichmenResult](bson)
          DeclarationRejectionEnrichment(drer.eisSubmissionDateTime)
      })

  override def lookupMetadata(submissionId: String)(
    implicit lc: LoggingContext): Future[Either[MetadataLookupError, ReplayMetadata]] =
    Mdc
      .preservingMdc(
        collection
          .withReadPreference(ReadPreference.primaryPreferred())
          .find(equal("submissionId", submissionId))
          .headOption()
      )
      .map{
        case None =>
          ContextLogger.info(s"No metadata found")
          Left(MetadataLookupError.MetadataNotFound)
        case Some(edp) =>
          edp.payload.asOpt((JsPath \\ "metadata" \\ "messageType").read[MessageType]).flatMap{ messageType =>
            edp.payload.asOpt((JsPath \\ "itinerary" \\ "modeOfTransportAtBorder").read[String]).map{ mode =>
              Right(EntryDeclarationMetadataPersisted(
                edp.submissionId,
                edp.eori,
                edp.correlationId,
                messageType,
                mode,
                edp.receivedDateTime,
                edp.mrn
              ).toDomain)
            }
          }.fold[Either[MetadataLookupError, ReplayMetadata]]{
            ContextLogger.info(s"No metadata found due to processing error")
            Left(MetadataLookupError.DataFormatError)
          }{result => result}
      }

  override def setHousekeepingAt(submissionId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, equal("submissionId", submissionId))

  override def setHousekeepingAt(eori: String, correlationId: String, time: Instant): Future[Boolean] =
    setHousekeepingAt(time, and(equal("eori", eori), equal("correlationId", correlationId)))

  private def setHousekeepingAt(time: Instant, query: Bson): Future[Boolean] =
    Mdc
      .preservingMdc(
        collection
          .updateOne(query, set("housekeepingAt", time))
          .toFutureOption()
      )
      .map(_.exists(_.getMatchedCount > 0))

  override def housekeep(now: Instant): Future[Int] =
    Mdc.preservingMdc(
      Source.fromPublisher(
        collection
          .find[BsonValue](lte("housekeepingAt", now))
          .projection(fields(include("_id")))
          .sort(ascending("housekeepingAt"))
          .limit(appConfig.housekeepingRunLimit)
      )
      .batch(appConfig.housekeepingBatchSize, List(_)) { (deletions, element) =>
        element :: deletions
      }
      .mapAsync(1) { deletions =>
        collection.bulkWrite(deletions.map(oid => DeleteManyModel(equal("_id", Codecs.fromBson[EntryObjectId](oid)._id))))
          .toFutureOption()
          .map(_.map(_.getDeletedCount).getOrElse(0))
          .recover{
            case _ =>
              logger.error(s"Failed to bulkWrite of deletions $deletions")
              0
          }
      }
      .runFold(0)(_ + _))

  override def getUndeliveredCounts: Future[UndeliveredCounts] = {
    import Aggregates._
    import Accumulators._

    val groupTransportType = group("$payload.itinerary.modeOfTransportAtBorder",
                                   first("transportMode", "$payload.itinerary.modeOfTransportAtBorder"),
                                   sum("count", 1))

    val groupIntoArray = group("",
                               sum("totalCount", "$count"),
                               push("transportCounts", BsonDocument("transportMode" -> "$transportMode",
                                                                    "count" -> "$count")))

    Mdc
      .preservingMdc(
        collection
          .aggregate[BsonValue](
            List(filter(undeliveredSubmissionsSelector(None)),
                 groupTransportType,
                 groupIntoArray)
          )
          .headOption()
      )
      .map {
        case Some(bson) => Codecs.fromBson[UndeliveredCounts](bson).sorted
        case None         => UndeliveredCounts(totalCount = 0, transportCounts = None)
      }
  }

  override def totalUndeliveredMessages(receivedNoLaterThan: Instant): Future[Int] =
    Mdc.preservingMdc(
      collection
        .countDocuments(undeliveredSubmissionsSelector(Some(receivedNoLaterThan)))
        .headOption()
        .map{
          case None => 0
          case Some(count) => count.toInt
        }
    )

  override def getUndeliveredSubmissionIds(receivedNoLaterThan: Instant, limit: Option[Int]): Source[String, NotUsed] =
    Source.fromPublisher(
      collection
        .find[BsonValue](undeliveredSubmissionsSelector(Some(receivedNoLaterThan)))
        .projection(fields(include("submissionId"), excludeId()))
        .sort(descending("receivedDateTime"))
        .limit(limit.getOrElse(0))
    )
    .map(Codecs.fromBson[SubmissionId](_).value)
    .mapMaterializedValue(_ => NotUsed)

  private def undeliveredSubmissionsSelector(optEndTime: Option[Instant]): Bson = {
    val stateClause = equal("eisSubmissionState", EisSubmissionState.mongoFormatString(EisSubmissionState.Error))
    optEndTime.fold(stateClause){endTime => and(stateClause, lte("receivedDateTime", endTime))}
  }
}
