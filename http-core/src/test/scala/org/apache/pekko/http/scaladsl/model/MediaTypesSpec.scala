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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.MediaTypes._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MediaTypesSpec extends AnyWordSpec with Matchers {
  "The registered WebAssembly, AVIF and zstd media types" should {
    "be available through the Java API" in {
      jm.MediaTypes.APPLICATION_WASM shouldEqual `application/wasm`
      jm.MediaTypes.IMAGE_AVIF shouldEqual `image/avif`
      jm.MediaTypes.APPLICATION_ZSTD shouldEqual `application/zstd`
    }

    "parse as binary content types" in {
      for (mediaType <- Seq(`application/wasm`, `image/avif`, `application/zstd`)) {
        ContentType.parse(mediaType.value) shouldEqual Right(ContentType(mediaType))
        ContentType.parse(mediaType.value.toUpperCase(java.util.Locale.ROOT)) shouldEqual Right(ContentType(mediaType))
      }
    }

    "be found by file extension, ignoring case" in {
      for ((extension, mediaType) <- Seq("wasm" -> `application/wasm`, "avif" -> `image/avif`,
          "zst" -> `application/zstd`)) {
        MediaTypes.forExtension(extension) shouldEqual mediaType
        MediaTypes.forExtension(extension.toUpperCase(java.util.Locale.ROOT)) shouldEqual mediaType
      }
    }
  }
}
