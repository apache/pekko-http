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

package org.apache.pekko.http.impl.engine.ws

import org.openjdk.jmh.annotations.Benchmark

import org.apache.pekko
import pekko.http.CommonBenchmark
import pekko.util.ByteString

class MaskingBench extends CommonBenchmark {
  val data = ByteString(new Array[Byte](10000))
  val mask = 0xFEDCBA09

  @Benchmark
  def benchRequestProcessing(): (ByteString, Int) =
    FrameEventParser.mask(data, mask)
}
