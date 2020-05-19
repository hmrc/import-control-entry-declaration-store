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

package uk.gov.hmrc.entrydeclarationstore.connectors

import org.scalamock.handlers.CallHandler
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import uk.gov.hmrc.auth.core.authorise._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait MockAuthConnector extends MockFactory {
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  object MockAuthConnector {
    def authorise[A](
      predicate: Predicate,
      retrieval: Retrieval[A],
      headerCarrier: HeaderCarrier): CallHandler[Future[A]] =
      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicate, retrieval, headerCarrier, *)
  }

}
