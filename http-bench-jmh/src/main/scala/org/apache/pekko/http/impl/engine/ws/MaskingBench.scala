/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.ws

import org.openjdk.jmh.annotations.Benchmark

import org.apache.pekko
import pekko.util.ByteString
import pekko.http.CommonBenchmark

class MaskingBench extends CommonBenchmark {
  val data = ByteString(new Array[Byte](10000))
  val mask = 0xFEDCBA09

  @Benchmark
  def benchRequestProcessing(): (ByteString, Int) =
    FrameEventParser.mask(data, mask)
}
