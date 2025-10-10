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

package org.apache.pekko.http.scaladsl.model

import java.net.{ InetAddress, InetSocketAddress, UnknownHostException }
import java.util.Optional
import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.javadsl.{ model => jm }
import pekko.http.impl.util.JavaMapping.Implicits._

sealed abstract class RemoteAddress extends jm.RemoteAddress with ValueRenderable {
  def toOption: Option[InetAddress]
  def toIP: Option[RemoteAddress.IP]
  def isUnknown: Boolean

  /** Java API */
  def getAddress: Optional[InetAddress] = toOption.asJava

  /** Java API */
  def getPort: Int = toIP.flatMap(_.port).getOrElse(0)
}

object RemoteAddress {
  case object Unknown extends RemoteAddress {
    def toOption = None
    def toIP = None

    def render[R <: Rendering](r: R): r.type = r ~~ "unknown"

    def isUnknown = true
  }

  final case class IP(ip: InetAddress, port: Option[Int] = None) extends RemoteAddress {
    def toOption: Option[InetAddress] = Some(ip)
    def toIP = Some(this)
    def render[R <: Rendering](r: R): r.type = {
      r ~~ ip.getHostAddress
      if (port.isDefined) r ~~ ":" ~~ port.get

      r
    }

    def isUnknown = false
  }

  def apply(a: InetAddress, port: Option[Int] = None): IP = IP(a, port)

  def apply(a: InetSocketAddress): IP = IP(a.getAddress, Some(a.getPort))

  def apply(bytes: Array[Byte]): RemoteAddress = {
    require(bytes.length == 4 || bytes.length == 16)
    try IP(InetAddress.getByAddress(bytes))
    catch { case _: UnknownHostException => Unknown }
  }

  private[pekko] val renderWithoutPort = new Renderer[RemoteAddress] {
    def render[R <: Rendering](r: R, address: RemoteAddress): r.type = address match {
      case IP(ip, _) => r ~~ ip.getHostAddress
      case _         => r ~~ address
    }
  }
}
