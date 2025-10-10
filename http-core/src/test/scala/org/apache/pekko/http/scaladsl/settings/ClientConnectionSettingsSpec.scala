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

class ClientConnectionSettingsSpec extends PekkoSpec {
  "ClientConnectionSettings" should {
    "fail early when creating ClientConnectionSettings with incomplete ParserSettings" in {
      // This creates 'generic' parserSettings, without client-specific (or server-specific) values.
      val parserSettings = ParserSettings(system)

      // This includes ParserSettings, complete with client-specific settings
      val clientConnectionSettings = ClientConnectionSettings(system)

      // This would create 'defective' clientConnectionSettings, since these
      // parserSettings don't contain the client or server-specific values. Notably,
      // max-content-length would not be set (and throw an exception on access).
      val e = intercept[IllegalArgumentException] {
        clientConnectionSettings.withParserSettings(parserSettings)
      }
      e.getMessage should include("does not contain the client-specific settings")
    }
  }
}
