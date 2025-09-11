/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko.testkit.PekkoSpec

class PreviewServerSettingsSpec extends PekkoSpec {

  def compileOnlySpec(body: => Unit) = ()

  "PreviewServerSettings" should {
    "compile when set programmatically" in compileOnlySpec {
      ServerSettings(system)
        .withPreviewServerSettings(PreviewServerSettings(system).withEnableHttp2(true))
    }
    "work get right defaults" in {
      val it: PreviewServerSettings = PreviewServerSettings(system)
      it.enableHttp2 should ===(false)
    }
  }
}
