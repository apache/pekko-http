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

* A bare `Partitioned` attribute in a parsed `Set-Cookie` header is now stored in the typed
  `HttpCookie.partitioned` field instead of the generic `extension` field. If your code detects
  partitioned cookies by inspecting `extension` (`getExtension()` in Java), use `partitioned`
  (`partitioned()` in Java) instead ([PR1562](https://github.com/apache/pekko-http/pull/1562)).
* An empty `Cache-Control` field is retained as a `RawHeader` without a parse warning, as zero directives are allowed by
  [RFC 9111, Section 5.2](https://www.rfc-editor.org/rfc/rfc9111.html#section-5.2). The modeled `Cache-Control` type still
  requires at least one directive, so an empty field does not change typed header lookup.
