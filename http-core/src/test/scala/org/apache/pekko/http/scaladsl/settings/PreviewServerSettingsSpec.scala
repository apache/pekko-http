/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko.testkit.AkkaSpec

class PreviewServerSettingsSpec extends AkkaSpec {

  def compileOnlySpec(body: => Unit) = ()

  "PreviewServerSettings" should {
    "compile when set programmatically" in compileOnlySpec {
      ServerSettings(system)
        .withPreviewServerSettings(PreviewServerSettings(system).withEnableHttp2(true))
        .withRemoteAddressHeader(true)
    }
    "work get right defaults" in {
      val it: PreviewServerSettings = PreviewServerSettings(system)
      it.enableHttp2 should ===(false)
    }
  }
}
