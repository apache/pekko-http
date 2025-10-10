/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.unmarshalling

import scala.concurrent.Future
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.unmarshalling.Unmarshaller.EitherUnmarshallingException
import pekko.http.scaladsl.util.FastFuture

trait GenericUnmarshallers extends LowerPriorityGenericUnmarshallers {

  implicit def liftToTargetOptionUnmarshaller[A, B](um: Unmarshaller[A, B]): Unmarshaller[A, Option[B]] =
    targetOptionUnmarshaller(um)
  implicit def targetOptionUnmarshaller[A, B](implicit um: Unmarshaller[A, B]): Unmarshaller[A, Option[B]] =
    um.map(Some(_)).withDefaultValue(None)
}

sealed trait LowerPriorityGenericUnmarshallers {

  implicit def messageUnmarshallerFromEntityUnmarshaller[T](
      implicit um: FromEntityUnmarshaller[T]): FromMessageUnmarshaller[T] =
    Unmarshaller.withMaterializer { implicit ec => implicit mat => request => um(request.entity) }

  implicit def liftToSourceOptionUnmarshaller[A, B](um: Unmarshaller[A, B]): Unmarshaller[Option[A], B] =
    sourceOptionUnmarshaller(um)
  implicit def sourceOptionUnmarshaller[A, B](implicit um: Unmarshaller[A, B]): Unmarshaller[Option[A], B] =
    Unmarshaller.withMaterializer(implicit ec =>
      implicit mat => {
        case Some(a) => um(a)
        case None    => FastFuture.failed(Unmarshaller.NoContentException)
      })

  /**
   * Enables using [[Either]] to encode the following unmarshalling logic:
   * Attempt unmarshalling the entity as as `R` first (yielding `R`),
   * and if it fails attempt unmarshalling as `L` (yielding `Left`).
   *
   * The either unmarshaller only works with strict entities, so make sure to wrap routes that want to use it with
   * `toStrictEntity`. Otherwise, if a non-strict entity is provided, it will fail with an `IllegalArgumentException`.
   *
   * Note that the Either's "R" type will be attempted first (as Left is often considered as the "failed case" in Either).
   */
  implicit def eitherUnmarshaller[L, R](implicit ua: FromEntityUnmarshaller[L], rightTag: ClassTag[R],
      ub: FromEntityUnmarshaller[R], leftTag: ClassTag[L]): FromEntityUnmarshaller[Either[L, R]] =
    Unmarshaller.withMaterializer { implicit ex => implicit mat => entity =>
      if (!entity.isStrict) LowerPriorityGenericUnmarshallers.needsStrictEntityFailure
      else {

        import pekko.http.scaladsl.util.FastFuture._
        @inline def right = ub(entity).fast.map(Right(_))

        @inline def fallbackLeft: PartialFunction[Throwable, Future[Either[L, R]]] = {
          case rightFirstEx =>
            val left = ua(entity).fast.map(Left(_))

            // combine EitherUnmarshallingException by carrying both exceptions
            left.recoverWith {
              case leftSecondEx =>
                Future.failed(
                  new EitherUnmarshallingException(
                    rightClass = rightTag.runtimeClass, right = rightFirstEx,
                    leftClass = leftTag.runtimeClass, left = leftSecondEx))
            }
        }

        right.recoverWith(fallbackLeft)
      }
    }
}

/**
 * Internal API
 */
@InternalApi
private[unmarshalling] object LowerPriorityGenericUnmarshallers {
  val needsStrictEntityFailure = Future.failed(new IllegalArgumentException(
    "eitherUnmarshaller only works with strict entities, so make sure to wrap routes that want to use it with `toStrictEntity`"))
}
