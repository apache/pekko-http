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

package org.apache.pekko.http;

import org.apache.pekko.http.javadsl.model.HttpMethod;
import org.apache.pekko.http.javadsl.model.HttpProtocol;
import org.apache.pekko.http.javadsl.model.Uri;

/**
 * Immutable request metadata for an {@link ExpectContinuePolicy} decision. The context does not
 * grant access to a request or response body, and cannot initiate a retry.
 *
 * @since 2.1.0
 */
public final class ExpectContinueRetryContext {
  private final HttpMethod method;
  private final Uri requestUri;
  private final HttpProtocol protocol;
  private final long contentLength;

  ExpectContinueRetryContext(
      HttpMethod method, Uri requestUri, HttpProtocol protocol, long contentLength) {
    this.method = method;
    this.requestUri = requestUri;
    this.protocol = protocol;
    this.contentLength = contentLength;
  }

  public HttpMethod getMethod() {
    return method;
  }

  /** The application's request URI, which can be relative for a connection-level request. */
  public Uri getRequestUri() {
    return requestUri;
  }

  /** The HTTP protocol in use. */
  public HttpProtocol getProtocol() {
    return protocol;
  }

  /** The number of bytes in the strict request body. */
  public long getContentLength() {
    return contentLength;
  }
}
