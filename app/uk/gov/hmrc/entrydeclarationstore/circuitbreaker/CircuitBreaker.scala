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

package uk.gov.hmrc.entrydeclarationstore.circuitbreaker

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import javax.inject.Inject
import uk.gov.hmrc.entrydeclarationstore.circuitbreaker.CircuitBreakerActor.{CallResult, MakeCall}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

/**
  * Circuit breaker that uses external shared open/closed state.
  *
  * Opens automatically after a number of successive failures
  * (or when the state is updated externally by another microservice instance)
  * but which must be manually closed by updating the external state.
  */
class CircuitBreaker @Inject()(circuitBreakerActorFactory: CircuitBreakerActor.Factory, circuitBreakerConfig: CircuitBreakerConfig)(
  implicit actorSystem: ActorSystem, ec: ExecutionContext) {
  implicit val timeout: Timeout = Timeout(circuitBreakerConfig.callTimeout.plus(2.seconds))
  val circuitBreakerActor: ActorRef = circuitBreakerActorFactory.apply(actorSystem)

  def withCircuitBreaker[T: ClassTag](body: => Future[T], defineFailureFn: Try[T] => Boolean): Future[T] =
    (circuitBreakerActor ? MakeCall(_ => body, defineFailureFn)).mapTo[CallResult[T]].map(_.value)
}
