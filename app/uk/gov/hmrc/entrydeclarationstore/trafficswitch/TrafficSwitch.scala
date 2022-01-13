/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationstore.trafficswitch

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import uk.gov.hmrc.entrydeclarationstore.trafficswitch.TrafficSwitchActor.{CallResult, MakeCall}

import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

/**
  * Traffic Switch that uses external shared not flowing/flowing state.
  *
  * Stops traffic flow automatically after a number of successive failures
  * (or when the state is updated externally by another microservice instance)
  * but which must be manually started again by updating the external state.
  */
class TrafficSwitch @Inject()(trafficSwitchActorFactory: TrafficSwitchActor.Factory, trafficSwitchConfig: TrafficSwitchConfig)(
  implicit actorSystem: ActorSystem, ec: ExecutionContext) {
  implicit val timeout: Timeout = Timeout(trafficSwitchConfig.callTimeout.plus(2.seconds))
  val trafficSwitchActor: ActorRef = trafficSwitchActorFactory.apply(actorSystem)

  def withTrafficSwitch[T: ClassTag](body: => Future[T], defineFailureFn: Try[T] => Boolean): Future[T] =
    (trafficSwitchActor ? MakeCall(_ => body, defineFailureFn)).mapTo[CallResult[T]].map(_.value)
}
