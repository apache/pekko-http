/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko.testkit.PekkoSpec

class ServerSettingsSpec extends PekkoSpec {
  "ServerSettings" should {
    "fail early when creating ServerSettings with incomplete ParserSettings" in {
      // This creates 'generic' parserSettings, without server-specific (or client-specific) values.
      val parserSettings = ParserSettings(system)

      // This includes ParserSettings, complete with server-specific settings
      val serverSettings = ServerSettings(system)

      // This would create 'defective' serverSettings, since these
      // parserSettings don't contain the server or client-specific values. Notably,
      // max-content-length would not be set (and throw an exception on access).
      val e = intercept[IllegalArgumentException] {
        serverSettings.withParserSettings(parserSettings)
      }
      e.getMessage should include("does not contain the server-specific settings")
    }
  }
}
