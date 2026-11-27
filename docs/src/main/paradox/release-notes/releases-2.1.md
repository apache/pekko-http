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
* The default rejection handler now returns `415 Unsupported Media Type` instead of `400 Bad Request`
  for an unsupported request content coding. When the route reports supported concrete codings, they
  are listed in the `Accept-Encoding` response field. A `415` for an unsupported content type does
  not add this field. Update clients or tests that expect `400` for an unsupported request content
  coding. Custom rejection handlers still control their own responses.
