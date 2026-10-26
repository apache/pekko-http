/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl

import java.util.Objects

import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.javadsl.model.HttpResponse
import pekko.japi.function.{ Function, Function2 }

/**
 * The outcome of sending a request through a connection pool flow: either the [[HttpResponse]] or the
 * failure that prevented one from being received.
 *
 * Emitted by the `*WithResult` connection pool flows of [[Http]] as a Java-friendly alternative to `scala.util.Try`.
 *
 * @since 2.0.0
 */
final class HttpResponseResult private (private val responseOrNull: HttpResponse,
    private val failureOrNull: Throwable) {

  /** Returns `true` if a response was received. */
  def isSuccess: Boolean = failureOrNull eq null

  /** Returns `true` if no response was received because of a failure. */
  def isFailure: Boolean = failureOrNull ne null

  /**
   * Returns the response.
   *
   * @throws IllegalStateException if this result is a failure; the failure is attached as the cause
   */
  def getResponse: HttpResponse =
    if (isSuccess) responseOrNull
    else throw new IllegalStateException("No response available, the request failed", failureOrNull)

  /**
   * Returns the failure.
   *
   * @throws IllegalStateException if this result is a success
   */
  def getFailure: Throwable =
    if (isFailure) failureOrNull
    else throw new IllegalStateException("No failure available, the request succeeded")

  /**
   * Applies `onSuccess` to the response if this result is a success, otherwise applies `onFailure` to the failure. Exceptions
   * thrown by either function are rethrown to the caller.
   */
  def fold[R](onSuccess: Function[HttpResponse, R], onFailure: Function[Throwable, R]): R =
    if (isSuccess) onSuccess.apply(responseOrNull) else onFailure.apply(failureOrNull)

  /**
   * Like `CompletionStage.handle`, applies `f` to the response and a `null` failure if this result is a
   * success, or to a `null` response and the failure otherwise. Exceptions thrown by `f` are rethrown to the caller.
   */
  def handle[R](f: Function2[HttpResponse, Throwable, R]): R = f.apply(responseOrNull, failureOrNull)

  override def equals(other: Any): Boolean = other match {
    case that: HttpResponseResult => Objects.equals(responseOrNull, that.responseOrNull) &&
      Objects.equals(failureOrNull, that.failureOrNull)
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(responseOrNull, failureOrNull)

  override def toString: String =
    if (isSuccess) s"HttpResponseResult.success($responseOrNull)" else s"HttpResponseResult.failure($failureOrNull)"
}

object HttpResponseResult {

  /** Creates a successful result holding the given response. */
  def success(response: HttpResponse): HttpResponseResult =
    new HttpResponseResult(Objects.requireNonNull(response, "response"), null)

  /** Creates a failed result holding the given failure. */
  def failure(failure: Throwable): HttpResponseResult =
    new HttpResponseResult(null, Objects.requireNonNull(failure, "failure"))

  /** INTERNAL API */
  @InternalApi
  private[http] def fromTry(result: Try[HttpResponse]): HttpResponseResult = result match {
    case Success(response) => success(response)
    case Failure(ex)       => failure(ex)
  }
}
