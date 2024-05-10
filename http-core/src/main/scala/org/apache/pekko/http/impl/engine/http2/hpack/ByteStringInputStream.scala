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

package org.apache.pekko.http.impl.engine.http2.hpack

import java.io.{ ByteArrayInputStream, InputStream }
import java.lang.invoke.{ MethodHandles, MethodType }

import scala.util.Try

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import pekko.util.ByteString.ByteString1C

/** INTERNAL API */
@InternalApi
private[http2] object ByteStringInputStream {

  private val byteStringInputStreamMethodTypeOpt = Try {
    val lookup = MethodHandles.publicLookup()
    val inputStreamMethodType = MethodType.methodType(classOf[InputStream])
    lookup.findVirtual(classOf[ByteString], "asInputStream", inputStreamMethodType)
  }.toOption

  def apply(bs: ByteString): InputStream = bs match {
    case cs: ByteString1C =>
      getInputStreamUnsafe(cs)
    case _ =>
      if (byteStringInputStreamMethodTypeOpt.isDefined) {
        byteStringInputStreamMethodTypeOpt.get.invoke(bs).asInstanceOf[InputStream]
      } else {
        asByteArrayInputStream(bs.compact)
      }
  }

  def asByteArrayInputStream(bs: ByteString): ByteArrayInputStream = bs match {
    case cs: ByteString1C =>
      getInputStreamUnsafe(cs)
    case _ =>
      // NOTE: We actually measured recently, and compact + use array was pretty good usually
      getInputStreamUnsafe(bs.compact)
  }

  private def getInputStreamUnsafe(bs: ByteString): ByteArrayInputStream =
    new ByteArrayInputStream(bs.toArrayUnsafe())

  private def asString(stream: InputStream): String = {
    val bos = new java.io.ByteArrayOutputStream()
    val array = new Array[Byte](4096)
    try {
      var n = stream.read(array)
      while (n != -1) {
        bos.write(array, 0, n)
        n = stream.read(array)
      }
      bos.toString("UTF-8")
    } finally {
      bos.close()
    }
  }

}
