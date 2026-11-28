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


# 0. Release Notes (2.1.x)

## 2.1.0

Pekko HTTP 2.1.0 has not been released yet. These notes list the changes merged so far.

### Behavior Changes

* Server `Accept` negotiation now compares media parameter names case-insensitively, per
  [RFC 9110 §5.6.6](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.6). Parameter values are compared as
  before, and the public `MediaRange.matches` method and inbound content-type matching are unchanged.

* A bare `Partitioned` attribute in a parsed `Set-Cookie` header is now stored in the typed
  `HttpCookie.partitioned` field instead of the generic `extension` field. If your code detects
  partitioned cookies by inspecting `extension` (`getExtension()` in Java), use `partitioned`
  (`partitioned()` in Java) instead ([PR1562](https://github.com/apache/pekko-http/pull/1562)).

* Empty `Accept-Charset` and `Accept-Language` fields now parse as typed headers with empty range lists,
  rather than as raw headers. Scala and Java constructors also accept empty lists. Response selection is
  unchanged: an empty field continues to use the same negotiation defaults as an absent field.

* Explicitly decoding a response with a built-in coder now removes only its matching outermost content coding.
  Selecting a coder that does not match leaves the response unchanged ([#1507](https://github.com/apache/pekko-http/issues/1507)).
