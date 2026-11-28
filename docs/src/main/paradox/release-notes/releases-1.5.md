<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# 0. Release Notes (1.5.x)

## 1.5.0

Release notes for Apache Pekko HTTP 1.5.0. See [GitHub Milestone for 1.5.0](https://github.com/apache/pekko-http/milestone/19?closed=1) for a fuller list of changes.

This release is binary compatible with Pekko HTTP 1.4.x. It adds a limit on the size of incoming HTTP/2 frames and includes further hardening changes to the HTTP/1 and HTTP/2 message handling. Several of the changes reject input that 1.4.x accepted; see the Behavior Changes below.

It is recommended that you use the latest Pekko 1.x release with this release. Pekko HTTP 1.5 jars are built with Pekko 1.1 jars and work with any later Pekko 1.x release. It is strongly recommended that you avoid using Pekko 1.0.x jars; we don't expect there to be problems running with them, but they are older than the jars this release is built with.

### Behavior Changes
* HTTP/2: the size of an incoming frame is bounded by the new `max-frame-size` setting
  (`pekko.http.server.http2.max-frame-size` and `pekko.http.client.http2.max-frame-size`, default `512kB`).
  A larger frame is rejected with `FRAME_SIZE_ERROR` before its payload is buffered. Earlier 1.x releases accepted
  frames of up to 16 MiB - 1. No larger `SETTINGS_MAX_FRAME_SIZE` is advertised, so a peer that follows RFC 9113
  keeps to 16 KiB frames and is not affected. The setting can be raised, up to 16 MiB - 1, to restore the earlier
  leniency ([PR1338](https://github.com/apache/pekko-http/pull/1338))
* Validate custom status code reason phrases. Constructing a `StatusCode` whose reason contains DEL, a control
  character other than HTAB, or a character above `U+00FF` now throws `IllegalArgumentException`. When a
  response with an unregistered status code is parsed, such characters are dropped from the received reason
  ([PR1514](https://github.com/apache/pekko-http/pull/1514))
* HTTP/1.1: the chunked transfer coding now requires CRLF line ends, as specified in
  [RFC 9112 section 7.1](https://www.rfc-editor.org/rfc/rfc9112#section-7.1). A bare LF at the end of a
  chunk-size line, at the end of a chunk extension or after the chunk data is no longer accepted, and nor is a
  control character other than HTAB in a chunk extension. This applies to requests and to responses; a peer
  that sends such a message now gets an entity stream error. A bare LF is still accepted in the start line, in
  header fields and in trailer fields ([PR1845](https://github.com/apache/pekko-http/pull/1845))
* HTTP/1.0: a message with a `Transfer-Encoding` header is now rejected, whether or not it also has a
  `Content-Length` header. HTTP/1.0 has no transfer codings, and such a message was previously framed as
  chunked. The server answers such a request with `400 Bad Request` and closes the connection, and the client
  fails such a response ([PR1844](https://github.com/apache/pekko-http/pull/1844))
* HTTP/2 server: the `content-length` declared by a request is now checked against the DATA frames received.
  If they do not match, the stream is reset with `RST_STREAM(PROTOCOL_ERROR)` and a request entity that has
  already been dispatched fails with an `EntityStreamException`. Other streams on the connection are not
  affected. Previously such a request was delivered with whatever data had arrived
  ([PR1846](https://github.com/apache/pekko-http/pull/1846))
* Client: the target host name of an HTTP(S) proxy `CONNECT` must be non-empty and contain only visible ASCII
  characters. A host name with whitespace, control characters or non-ASCII characters is now rejected with an
  `IllegalArgumentException`, as it would otherwise change the request sent to the proxy. This affects host
  names passed as a `String`, for instance to `ClientTransport.connectTo`; an internationalized domain name
  has to be given in its ASCII (Punycode) form. Host names taken from a parsed `Uri` are not affected
  ([PR1843](https://github.com/apache/pekko-http/pull/1843))

### Bug Fixes
* Client: handle the framing of empty HTTP/1 `205 Reset Content` responses, so that an empty chunked `205` no
  longer corrupts the next response on a persistent connection. A `205` with a non-empty chunk or a non-zero
  `Content-Length` is rejected ([PR1516](https://github.com/apache/pekko-http/pull/1516))
* Keep the line end after an invalid UTF-8 sequence in a header value, so that the following header line is no
  longer read as part of that value ([PR1512](https://github.com/apache/pekko-http/pull/1512))
* HTTP/2: decode UTF-8 in header values as HTTP/1.1 does, and keep octets above `0x7F` intact. A value such as
  `café` was previously surfaced as `cafÃ©` ([PR1537](https://github.com/apache/pekko-http/pull/1537))
* Read a folded header line the same way whether or not its first line is cached. A folded header whose first
  line had been sent earlier on the connection, or matched a predefined value such as `Accept: */*`, was
  rejected with `400 Bad Request` ([PR1842](https://github.com/apache/pekko-http/pull/1842))
* Generate the default multipart boundary with a `SecureRandom`. The boundary format and length are unchanged,
  and an explicitly supplied boundary or `Random` is still used as given
  ([PR1847](https://github.com/apache/pekko-http/pull/1847))

### Documentation
* Warn that `getFromResource` does not filter its resource name, so a name built from untrusted input needs to
  be validated by the caller ([PR1410](https://github.com/apache/pekko-http/pull/1410))

### Dependency Changes
* Jackson 2.21.9 ([PR1363](https://github.com/apache/pekko-http/pull/1363), [PR1837](https://github.com/apache/pekko-http/pull/1837))
