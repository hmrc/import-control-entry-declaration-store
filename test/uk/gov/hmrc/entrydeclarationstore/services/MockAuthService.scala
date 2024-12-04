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

package uk.gov.hmrc.entrydeclarationstore.services

import org.scalamock.handlers.CallHandler
import org.scalamock.matchers.ArgCapture.CaptureOne
import play.api.mvc.Headers
import uk.gov.hmrc.entrydeclarationstore.utils.TestHarness
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait MockAuthService extends TestHarness {
  val mockAuthService: AuthService = mock[AuthService]

  object MockAuthService {
    def authenticate: CallHandler[Future[Option[UserDetails]]] =
      (mockAuthService.authenticate(_: HeaderCarrier, _: Headers)).expects(*, *)

    def authenticateCapture(headerCarrier: CaptureOne[HeaderCarrier]): CallHandler[Future[Option[UserDetails]]] =
      (mockAuthService.authenticate(_: HeaderCarrier, _: Headers)).expects(capture(headerCarrier), *)
  }

}
