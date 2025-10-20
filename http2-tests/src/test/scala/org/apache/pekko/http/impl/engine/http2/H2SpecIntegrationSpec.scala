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

package org.apache.pekko.http.impl.engine.http2

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.pekko
import pekko.http.impl.util.{ ExampleHttpContexts, WithLogCapturing }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.server.Directives
import pekko.testkit._
import pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process._

class H2SpecIntegrationSpec extends PekkoFreeSpec(
      """
     pekko {
       loglevel = DEBUG
       loggers = ["org.apache.pekko.http.impl.util.SilenceAllTestEventListener"]
       http.server.log-unencrypted-network-bytes = 100
       http.server.enable-http2 = on
       http.server.http2.log-frames = on

       actor.serialize-creators = off
       actor.serialize-messages = off

       stream.materializer.debug.fuzzing-mode = off
     }
  """) with Directives with ScalaFutures with WithLogCapturing {

  implicit val ec: ExecutionContext = system.dispatcher

  override def expectedTestDuration = 5.minutes // because slow jenkins, generally finishes below 1 or 2 minutes

  val echo = entity(as[ByteString]) { data =>
    complete(data)
  }

  val binding =
    Http()
      .newServerAt("127.0.0.1", 0)
      .enableHttps(ExampleHttpContexts.exampleServerContext)
      .bind(echo)
      .futureValue
  val port = binding.localAddress.getPort

  "H2Spec" - {

    /**
     * This list can be exported using `h2spec --dryrun`
     */
    val testCaseDesc =
      """
      3. Starting HTTP/2
        3.5. HTTP/2 Connection Preface
          1: Sends client connection preface
          2: Sends invalid connection preface

      4. HTTP Frames
        4.1. Frame Format
          1: Sends a frame with unknown type
          2: Sends a frame with undefined flag
          (pending) 3: Sends a frame with reserved field bit

        4.2. Frame Size
          1: Sends a DATA frame with 2^14 octets in length
          (pending) 2: Sends a large size DATA frame that exceeds the SETTINGS_MAX_FRAME_SIZE
          (pending) 3: Sends a large size HEADERS frame that exceeds the SETTINGS_MAX_FRAME_SIZE

        4.3. Header Compression and Decompression
          1: Sends invalid header block fragment
          2: Sends a PRIORITY frame while sending the header blocks
          3: Sends a HEADERS frame to another stream while sending the header blocks

      5. Streams and Multiplexing
        5.1. Stream States
          1: idle: Sends a DATA frame
          2: idle: Sends a RST_STREAM frame
          3: idle: Sends a WINDOW_UPDATE frame
          4: idle: Sends a CONTINUATION frame
          (pending) 5: half closed (remote): Sends a DATA frame
          (pending) 6: half closed (remote): Sends a HEADERS frame
          7: half closed (remote): Sends a CONTINUATION frame
          (pending) 8: closed: Sends a DATA frame after sending RST_STREAM frame
          (pending) 9: closed: Sends a HEADERS frame after sending RST_STREAM frame
          10: closed: Sends a CONTINUATION frame after sending RST_STREAM frame
          (pending) 11: closed: Sends a DATA frame
          (pending) 12: closed: Sends a HEADERS frame
          13: closed: Sends a CONTINUATION frame

          5.1.1. Stream Identifiers
            (pending) 1: Sends even-numbered stream identifier
            2: Sends stream identifier that is numerically smaller than previous

          5.1.2. Stream Concurrency
            (pending) 1: Sends HEADERS frames that causes their advertised concurrent stream limit to be exceeded

        5.3. Stream Priority
          5.3.1. Stream Dependencies
            1: Sends HEADERS frame that depends on itself
            2: Sends PRIORITY frame that depend on itself

        5.4. Error Handling
          5.4.1. Connection Error Handling
            1: Sends an invalid PING frame for connection close

        5.5. Extending HTTP/2
          1: Sends an unknown extension frame
          (pending) 2: Sends an unknown extension frame in the middle of a header block

      6. Frame Definitions
        6.1. DATA
          (pending) 1: Sends a DATA frame with 0x0 stream identifier
          (pending) 2: Sends a DATA frame on the stream that is not in "open" or "half-closed (local)" state
          (pending) 3: Sends a DATA frame with invalid pad length

        6.2. HEADERS
          1: Sends a HEADERS frame without the END_HEADERS flag, and a PRIORITY frame
          2: Sends a HEADERS frame to another stream while sending a HEADERS frame
          3: Sends a HEADERS frame with 0x0 stream identifier
          4: Sends a HEADERS frame with invalid pad length

        6.3. PRIORITY
          1: Sends a PRIORITY frame with 0x0 stream identifier
          2: Sends a PRIORITY frame with a length other than 5 octets

        6.4. RST_STREAM
          1: Sends a RST_STREAM frame with 0x0 stream identifier
          2: Sends a RST_STREAM frame on a idle stream
          3: Sends a RST_STREAM frame with a length other than 4 octets

        6.5. SETTINGS
          1: Sends a SETTINGS frame with ACK flag and payload
          2: Sends a SETTINGS frame with a stream identifier other than 0x0
          3: Sends a SETTINGS frame with a length other than a multiple of 6 octets

          6.5.2. Defined SETTINGS Parameters
            (pending) 1: SETTINGS_ENABLE_PUSH (0x2): Sends the value other than 0 or 1
            2: SETTINGS_INITIAL_WINDOW_SIZE (0x4): Sends the value above the maximum flow control window size
            (pending) 3: SETTINGS_MAX_FRAME_SIZE (0x5): Sends the value below the initial value
            (pending) 4: SETTINGS_MAX_FRAME_SIZE (0x5): Sends the value above the maximum allowed frame size
            5: Sends a SETTINGS frame with unknown identifier

          6.5.3. Settings Synchronization
            1: Sends multiple values of SETTINGS_INITIAL_WINDOW_SIZE
            2: Sends a SETTINGS frame without ACK flag

        6.7. PING
          1: Sends a PING frame
          2: Sends a PING frame with ACK
          3: Sends a PING frame with a stream identifier field value other than 0x0
          4: Sends a PING frame with a length field value other than 8

        6.8. GOAWAY
          1: Sends a GOAWAY frame with a stream identifier other than 0x0

        6.9. WINDOW_UPDATE
          1: Sends a WINDOW_UPDATE frame with a flow control window increment of 0
          2: Sends a WINDOW_UPDATE frame with a flow control window increment of 0 on a stream
          (pending) 3: Sends a WINDOW_UPDATE frame with a length other than 4 octets

          6.9.1. The Flow-Control Window
            1: Sends SETTINGS frame to set the initial window size to 1 and sends HEADERS frame
            (pending) 2: Sends multiple WINDOW_UPDATE frames increasing the flow control window to above 2^31-1
            (pending) 3: Sends multiple WINDOW_UPDATE frames increasing the flow control window to above 2^31-1 on a stream

          6.9.2. Initial Flow-Control Window Size
            1: Changes SETTINGS_INITIAL_WINDOW_SIZE after sending HEADERS frame
            2: Sends a SETTINGS frame for window size to be negative
            3: Sends a SETTINGS_INITIAL_WINDOW_SIZE settings with an exceeded maximum window size value

        6.10. CONTINUATION
          1: Sends multiple CONTINUATION frames preceded by a HEADERS frame
          2: Sends a CONTINUATION frame followed by any frame other than CONTINUATION
          3: Sends a CONTINUATION frame with 0x0 stream identifier
          4: Sends a CONTINUATION frame preceded by a HEADERS frame with END_HEADERS flag
          5: Sends a CONTINUATION frame preceded by a CONTINUATION frame with END_HEADERS flag
          6: Sends a CONTINUATION frame preceded by a DATA frame

      7. Error Codes
        1: Sends a GOAWAY frame with unknown error code
        2: Sends a RST_STREAM frame with unknown error code

      8. HTTP Message Exchanges
        8.1. HTTP Request/Response Exchange
          1: Sends a second HEADERS frame without the END_STREAM flag

          8.1.2. HTTP Header Fields
            (pending) 1: Sends a HEADERS frame that contains the header field name in uppercase letters

            8.1.2.1. Pseudo-Header Fields
              1: Sends a HEADERS frame that contains a unknown pseudo-header field
              2: Sends a HEADERS frame that contains the pseudo-header field defined for response
              3: Sends a HEADERS frame that contains a pseudo-header field as trailers
              4: Sends a HEADERS frame that contains a pseudo-header field that appears in a header block after a regular header field

            8.1.2.2. Connection-Specific Header Fields
              1: Sends a HEADERS frame that contains the connection-specific header field
              2: Sends a HEADERS frame that contains the TE header field with any value other than "trailers"

            8.1.2.3. Request Pseudo-Header Fields
              1: Sends a HEADERS frame with empty ":path" pseudo-header field
              2: Sends a HEADERS frame that omits ":method" pseudo-header field
              3: Sends a HEADERS frame that omits ":scheme" pseudo-header field
              4: Sends a HEADERS frame that omits ":path" pseudo-header field
              5: Sends a HEADERS frame with duplicated ":method" pseudo-header field
              6: Sends a HEADERS frame with duplicated ":scheme" pseudo-header field
              7: Sends a HEADERS frame with duplicated ":path" pseudo-header field

            8.1.2.6. Malformed Requests and Responses
              (pending) 1: Sends a HEADERS frame with the "content-length" header field which does not equal the DATA frame payload length
              (pending) 2: Sends a HEADERS frame with the "content-length" header field which does not equal the sum of the multiple DATA frames payload length
      """

    sealed trait TestElement
    case class TestGroup(
        number: String,
        name: String,
        elements: Seq[TestElement]) extends TestElement
    case class TestExample(
        number: String,
        name: String,
        isPending: Boolean) extends TestElement

    def parseTests(desc: String): TestGroup = {
      val lines =
        desc.split("\n")
          .map(_.trim)
          .filterNot(x => x.isEmpty || x.startsWith("#"))

      val GroupR = """((?:\d+\.)+)\s+(.*)""".r
      val ExampleR = """(\(pending\)\s+)?(\d+): (.*)""".r

      def parse(lines: Seq[String]): Seq[TestElement] = lines match {
        case ExampleR(pending, number, name) +: rest =>
          Seq(TestExample(number, name, pending ne null)) ++ parse(rest)
        case GroupR(number, name) +: rest =>
          def isChild(c: String): Boolean = c.startsWith(number) || c.contains(":")
          // naive O(n^2) implementation, because of unbounded look-ahead
          val _endOfGroup = rest.indexWhere(!isChild(_))
          val endOfGroup = if (_endOfGroup == -1) rest.length else _endOfGroup
          val groupElementLines = rest.take(endOfGroup)
          val elements = parse(groupElementLines)
          val group = TestGroup(number.dropRight(1), name, elements)
          Seq(group) ++ parse(rest.drop(endOfGroup))
        case Nil => Seq.empty
      }
      TestGroup("", "HTTP/2", parse(lines))
    }
    val testStructure: TestGroup = parseTests(testCaseDesc)

    // execution of tests ------------------------------------------------------------------
    {
      def setup(structure: TestGroup): Unit =
        s"${structure.number} ${structure.name}" - {
          val examples = structure.elements.collect { case e: TestExample => e }
          examples.foreach {
            case TestExample(number, name, isPending) =>
              val exampleNumber = s"${structure.number}/$number"
              if (isPending)
                name ignore {
                  // no need to run ignored tests, but we might reenable under pendingUntilFixed
                  // to find out when they are fixed
                  // runSpec(specSectionNumber = Some(exampleNumber),
                  //   junitOutput = new File(s"target/test-reports/h2spec-junit-$number.xml"))
                }
              else
                name in {
                  runSpec(specSectionNumber = Some(exampleNumber),
                    junitOutput = new File(s"target/test-reports/h2spec-junit-$number.xml"))
                }
          }
          val subgroups = structure.elements.collect { case g: TestGroup => g }
          subgroups.foreach(setup)
        }

      setup(testStructure)
    }
    // end of execution of tests -----------------------------------------------------------

    def runSpec(specSectionNumber: Option[String], junitOutput: File): Unit = {
      junitOutput.getParentFile.mkdirs()

      val TestFailureMarker = "×" // that special character is next to test failures, so we detect them by it

      val keepAccumulating = new AtomicBoolean(true)
      val stdout = new StringBuffer()
      val stderr = new StringBuffer()

      val command = Seq( // need to use Seq[String] form for command because executable path may contain spaces
        executable,
        "-k", "-t",
        "-p", port.toString,
        "-j", junitOutput.getPath) ++
        specSectionNumber.toList.map(number => s"http2/$number")

      log.debug(s"Executing h2spec: $command")
      val aggregateTckLogs = ProcessLogger(
        out => {
          if (out.contains("All tests passed")) ()
          else if (out.contains("tests, ")) ()
          else if (out.contains("===========================================")) keepAccumulating.set(false)
          else if (keepAccumulating.get) stdout.append(out + Console.RESET + "\n  ")
        },
        err => stderr.append(err))

      // p.exitValue blocks until the process is terminated
      val p = command.run(aggregateTckLogs)
      val exitedWith = p.exitValue()

      val output = stdout.toString
      stderr.toString should be("")
      output shouldNot startWith("Error:")
      output shouldNot include(TestFailureMarker)
      exitedWith should be(0)
    }

    def executable =
      System.getProperty("h2spec.path").ensuring(_ != null, "h2spec.path property not defined")
  }
}
