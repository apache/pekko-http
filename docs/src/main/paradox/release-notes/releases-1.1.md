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

# 0. Release Notes (1.1.x)

## 1.1.0

Release notes for Apache Pekko HTTP 1.1.0. See [GitHub Milestone for 1.1.0-M1](https://github.com/apache/pekko-http/milestone/1?closed=1) and [GitHub Milestone for 1.1.0](https://github.com/apache/pekko-http/milestone/4?closed=1) for a fuller list of changes.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars (or later 1.x releases) where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.1 jars are built with Pekko 1.1 jars.

All the changes in the @ref:[1.0.x releases](releases-1.0.md) up to and including v1.0.1.

### Bug Fix
* fix parse broken when header value is null ([PR575](https://github.com/apache/pekko-http/pull/575)) (not in v1.1.0-M1)

### Changes
* Changed names of HTTP status codes 413 and 422 ([PR87](https://github.com/apache/pekko-http/pull/87))
* Parse entire HTTP chunk size ([PR528](https://github.com/apache/pekko-http/pull/528))
* handle invalid Accept-Charset in requests - default to UTF-8 ([PR584](https://github.com/apache/pekko-http/pull/584)) (not in v1.1.0-M1)

### Additions
* Add UnsupportedContentTypeException Java DSL ([PR376](https://github.com/apache/pekko-http/pull/376))
* new pekko-http-testkit-munit module ([PR536](https://github.com/apache/pekko-http/pull/536))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* slf4j was updated to v2
* Jackson 2.17.2
