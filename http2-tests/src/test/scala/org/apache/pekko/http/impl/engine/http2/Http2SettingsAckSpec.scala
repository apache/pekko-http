/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{
  DataFrame,
  ParsedHeadersFrame,
  Setting,
  SettingsAckFrame,
  SettingsFrame
}
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier.SETTINGS_INITIAL_WINDOW_SIZE
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.model.{ HttpMethods, Uri }
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, Http2ServerSettings }
import pekko.stream.scaladsl.{ BidiFlow, Flow, Keep }
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.util.{ ByteString, OptionVal }

class Http2SettingsAckSpec extends PekkoSpecWithMaterializer("") {
  "The HTTP/2 demultiplexer" should {
    for (server <- Seq(true, false)) {
      val role = if (server) "server" else "client"
      s"acknowledge settings before releasing pending DATA on the $role" in assertAllStagesStopped {
        val client = ClientConnectionSettings(system)
        val demux =
          if (server) new Http2ServerDemux(Http2ServerSettings(""), Nil, upgraded = false)
          else new Http2ClientDemux(client.http2Settings, HttpHeaderParser(client.parserSettings, system.log))
        val peerTransport = Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(Keep.both)
        val ((send, (wire, peer)), received) = TestSource[Http2SubStream]()
          .viaMat(BidiFlow.fromGraph(demux).joinMat(peerTransport)(Keep.right))(Keep.both)
          .toMat(TestSink[Http2SubStream]())(Keep.both)
          .run()

        def window(n: Int): Setting = Setting(SETTINGS_INITIAL_WINDOW_SIZE, n)

        val uri = Uri("https://example.test/transfer")
        val request = ParsedHeadersFrame(1, endStream = server,
          Seq(
            ":method" -> (if (server) HttpMethods.GET else HttpMethods.POST),
            ":scheme" -> uri.scheme,
            ":authority" -> uri.authority,
            ":path" -> (uri.path, uri.rawQueryString)), None, None)
        val response = ParsedHeadersFrame(1, endStream = !server, Seq(":status" -> "200"), None, None)
        val outgoing = if (server) response else request

        try {
          wire.request(16)
          received.request(1)
          wire.expectNext() shouldBe a[SettingsFrame]
          val initial = Seq(window(2))
          peer.sendNext(SettingsFrame(initial))
          wire.expectNext() shouldBe SettingsAckFrame(initial)
          peer.sendNext(SettingsAckFrame(Nil))

          if (server) {
            peer.sendNext(request)
            received.expectNext().initialHeaders shouldBe request
          }

          send.sendNext(Http2SubStream(outgoing, OptionVal.None, Left(ByteString("abcd")), Map.empty))
          wire.expectNext() shouldBe outgoing
          wire.expectNext() shouldBe DataFrame(1, endStream = false, ByteString("ab"))

          // The final value in the frame replaces the intermediate one. No DATA may use it in between.
          val intermediate = Seq(window(4), window(2))
          peer.sendNext(SettingsFrame(intermediate))
          wire.expectNext() shouldBe SettingsAckFrame(intermediate)
          val increased = Seq(window(4))
          peer.sendNext(SettingsFrame(increased))
          wire.expectNext() shouldBe SettingsAckFrame(increased)
          wire.expectNext() shouldBe DataFrame(1, endStream = true, ByteString("cd"))

          if (!server) {
            peer.sendNext(response)
            received.expectNext().initialHeaders shouldBe response
          }
        } finally {
          wire.cancel()
          received.cancel()
        }
      }
    }
  }
}
