/*
 * Copyright 2024 HM Revenue & Customs
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

package utils

import cats.data.EitherT
import errors.{NoData, TrustErrors}

import scala.concurrent.{ExecutionContext, Future}


object TrustEnvelope {
  type TrustEnvelope[T] = EitherT[Future, TrustErrors, T]

  def apply[T](t: T): TrustEnvelope[T] = EitherT[Future, TrustErrors, T](Future.successful(Right(t)))

  def fromFuture[T](t: Future[T])(implicit ec: ExecutionContext): TrustEnvelope[T] = EitherT.right(t)

  def apply[T](eitherArg: Either[TrustErrors, T])(implicit ec: ExecutionContext): TrustEnvelope[T] =
    EitherT.fromEither[Future](eitherArg)

  def fromOption[T](optT: Option[T])(implicit ec: ExecutionContext) : TrustEnvelope[T] = {
    EitherT.fromOption[Future](optT, NoData)
  }
}

