/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine

import org.apache.pekko.util.ByteString

package object http2 {
  implicit class RichString(val str: String) extends AnyVal {
    def parseHexByteString: ByteString =
      ByteString(
        str.replaceAll("\\s", "").trim.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray)
  }
  implicit class HexInterpolatorString(val sc: StringContext) extends AnyVal {
    def hex(args: Any*): ByteString = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      val buf = new StringBuffer(strings.next())
      while (strings.hasNext) {
        buf.append(expressions.next())
        buf.append(strings.next())
      }
      buf.toString.parseHexByteString
    }
  }
}
