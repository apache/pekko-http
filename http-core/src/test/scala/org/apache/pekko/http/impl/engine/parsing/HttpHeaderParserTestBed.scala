/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.parsing

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.settings.ParserSettings
import com.typesafe.config.{ Config, ConfigFactory }

object HttpHeaderParserTestBed extends App {

  val testConf: Config = ConfigFactory.parseString("""
    pekko.event-handlers = ["org.apache.pekko.testkit.TestEventListener"]
    pekko.loglevel = ERROR
    pekko.http.parsing.max-header-name-length = 20
    pekko.http.parsing.max-header-value-length = 21
    pekko.http.parsing.header-cache.Host = 300""")
  val system: ActorSystem = ActorSystem("HttpHeaderParserTestBed", testConf)

  val parser = HttpHeaderParser.prime {
    HttpHeaderParser.unprimed(ParserSettings(system), system.log,
      warnOnIllegalHeader = info => system.log.warning(info.formatPretty))
  }

  println {
    s"""
       |HttpHeaderParser primed Trie
       |----------------------------
       |
       |%TRIE%
       |
       |formatSizes: ${parser.formatSizes}
       |contentHistogram: ${parser.contentHistogram.mkString("\n  ", "\n  ", "\n")}
     """.stripMargin.replace("%TRIE%", parser.formatTrie)
  }

  system.terminate()
}
