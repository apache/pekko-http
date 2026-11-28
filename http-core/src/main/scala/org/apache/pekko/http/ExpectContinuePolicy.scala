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

package org.apache.pekko.http

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.http.javadsl.{ model => jm }
import org.apache.pekko.http.scaladsl.model.headers.Expect
import org.apache.pekko.http.scaladsl.model.{ AttributeKey, HttpEntity, HttpProtocol, HttpProtocols, HttpRequest }

/**
 * A client policy for repeating an explicitly expected request once, without `Expect: 100-continue`.
 * A client that supports this recovery considers the policy only after receiving a final 417 while
 * it can still prove it has withheld a nonempty, strict body and the request has exactly one
 * modeled `Expect: 100-continue` header. It does not add an `Expect` field.
 *
 * Implementations should return promptly. The HTTP/1.1 request function only considers idempotent
 * methods, regardless of the policy. Do not enable recovery if removing `Expect` would invalidate
 * the request, for example if a signature covers that field.
 *
 * @since 2.1.0
 */
@FunctionalInterface
trait ExpectContinuePolicy {
  def shouldRetry(context: ExpectContinueRetryContext): Boolean
}

/** @since 2.1.0 */
object ExpectContinuePolicy {

  /** Preserve ordinary client behavior; do not retry on a 417. */
  val disabled: ExpectContinuePolicy = _ => false

  /** Allow one eligible retry when the HTTP method is idempotent. */
  val retryIdempotentOnce: ExpectContinuePolicy = _.getMethod.isIdempotent

  /**
   * A local request attribute for narrowing a supporting client's policy without changing its connection settings.
   * The client policy must permit the retry before the request policy is asked. An absent attribute inherits the
   * client policy; set [[disabled]] to opt out for one request. This attribute cannot enable disabled client recovery.
   */
  val attributeKey: jm.AttributeKey[ExpectContinuePolicy] = AttributeKey("pekko.http.client.expect-continue-policy")
}

/** INTERNAL API */
@InternalApi
private[http] object ExpectContinuePolicySupport {
  import ExpectContinuePolicy.{ attributeKey, disabled }

  def forRequest(request: HttpRequest, clientPolicy: ExpectContinuePolicy): ExpectContinuePolicy =
    if (clientPolicy eq disabled) disabled
    else
      request.attribute(attributeKey) match {
        case None                                             => clientPolicy
        case Some(requestPolicy) if requestPolicy eq disabled => disabled
        case Some(requestPolicy)                              =>
          context => clientPolicy.shouldRetry(context) && requestPolicy.shouldRetry(context)
      }

  def eligibleContentLength(
      request: HttpRequest,
      protocol: HttpProtocol,
      policy: ExpectContinuePolicy): Option[Long] = {
    if ((policy eq disabled) || (protocol != HttpProtocols.`HTTP/1.1` && protocol != HttpProtocols.`HTTP/2.0`)) None
    else
      request.entity match {
        case HttpEntity.Strict(_, data) if data.nonEmpty =>
          val expectations = request.headers.iterator.filter(_.is("expect"))
          if (expectations.hasNext && (expectations.next() eq Expect.`100-continue`) && !expectations.hasNext)
            Some(data.length.toLong)
          else None
        case _ => None
      }
  }

  // Only a dispatch owner that has verified an associated pre-content 417 should ask the policy.
  def retryContext(
      request: HttpRequest,
      protocol: HttpProtocol,
      policy: ExpectContinuePolicy): Option[ExpectContinueRetryContext] =
    eligibleContentLength(request, protocol, policy).map { length =>
      new ExpectContinueRetryContext(request.method, request.getUri, protocol, length)
    }
}
