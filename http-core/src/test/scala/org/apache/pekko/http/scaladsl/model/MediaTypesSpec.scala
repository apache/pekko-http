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

  "The JavaScript media types" should {
    "resolve JavaScript file extensions to text/javascript, ignoring case" in {
      for (extension <- Seq("js", "mjs", "JS", "MJS")) {
        MediaTypes.forExtension(extension) shouldEqual `text/javascript`
        MediaTypes.forExtensionOption(extension) shouldEqual Some(`text/javascript`)
      }
      `text/javascript`.fileExtensions shouldEqual List("js", "mjs")
    }

    "keep the legacy application/javascript type for explicit use and parsing" in {
      MediaType.parse("text/javascript") shouldEqual Right(`text/javascript`)
      MediaType.parse("application/javascript") shouldEqual Right(`application/javascript`)
      `application/javascript`.fileExtensions shouldEqual List("js", "mjs")
      ContentType.parse("text/javascript; charset=UTF-8") shouldEqual
      Right(`text/javascript`.withCharset(HttpCharsets.`UTF-8`))
    }
  }

  "The web app manifest media type" should {
    "use a fixed UTF-8 charset without rendering a charset parameter" in {
      val manifest: MediaType.WithFixedCharset = `application/manifest+json`
      manifest.charset shouldEqual HttpCharsets.`UTF-8`
      ContentType.parse("application/manifest+json") shouldEqual Right(ContentType(manifest))
      ContentType.parse("APPLICATION/MANIFEST+JSON") shouldEqual Right(ContentType(manifest))
      ContentType(manifest).toString shouldEqual "application/manifest+json"
    }

    "be found by the .webmanifest extension only, ignoring case" in {
      `application/manifest+json`.fileExtensions shouldEqual List("webmanifest")
      MediaTypes.forExtension("webmanifest") shouldEqual `application/manifest+json`
      MediaTypes.forExtension("WEBMANIFEST") shouldEqual `application/manifest+json`
      MediaTypes.forExtension("manifest") shouldEqual `text/cache-manifest`
      MediaTypes.forExtension("json") shouldEqual `application/json`
    }
  }

  "The AAC audio media type" should {
    "be a non-compressible binary media type" in {
      val aac: MediaType.Binary = `audio/aac`
      aac.isCompressible shouldBe false
      ContentType.parse("audio/aac") shouldEqual Right(ContentType(aac))
      ContentType.parse("AUDIO/AAC") shouldEqual Right(ContentType(aac))
      ContentType(aac).toString shouldEqual "audio/aac"
    }

    "be found by the .aac and .loas extensions, ignoring case" in {
      `audio/aac`.fileExtensions shouldEqual List("aac", "loas")
      for (extension <- Seq("aac", "AAC", "loas", "LOAS"))
        MediaTypes.forExtension(extension) shouldEqual `audio/aac`
    }
  }
}
