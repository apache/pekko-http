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

package org.apache.pekko.http.scaladsl.server

import java.net.InetSocketAddress

import scala.io.StdIn

import org.apache.pekko
import pekko.actor.{ ActorSystem, ActorSystemImpl }
import pekko.event.Logging
import pekko.stream.ActorAttributes
import pekko.stream.scaladsl._
import pekko.util.ByteString

import com.typesafe.config.{ Config, ConfigFactory }

object TcpLeakApp extends App {
  val testConf: Config = ConfigFactory.parseString(
    """
    pekko.loglevel = DEBUG
    pekko.log-dead-letters = on
    pekko.io.tcp.trace-logging = on""")
  implicit val system: ActorSystem = ActorSystem("ServerTest", testConf)

  import system.dispatcher

  val tcpFlow = Tcp(system).outgoingConnection(new InetSocketAddress("127.0.0.1", 1234)).named("TCP-outgoingConnection")
  List
    .fill(100)(
      Source
        .single(ByteString("FOO"))
        .log("outerFlow-beforeTcpFlow").withAttributes(ActorAttributes.logLevels(Logging.DebugLevel, Logging.ErrorLevel,
          Logging.ErrorLevel))
        .via(tcpFlow)
        .log("outerFlow-afterTcpFlow").withAttributes(ActorAttributes.logLevels(Logging.DebugLevel, Logging.ErrorLevel,
          Logging.ErrorLevel))
        .toMat(Sink.head)(Keep.right).run())
    .last
    .onComplete {
      result =>
        println(s"Result: $result")
        Thread.sleep(10000)
        println("===================== \n\n" +
          system.asInstanceOf[
            ActorSystemImpl].printTree + "\n\n========================")
    }

  Thread.sleep(11000)
  StdIn.readLine("Press Enter to stop the application")
  system.terminate()
}
