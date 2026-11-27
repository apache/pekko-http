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

package org.apache.pekko.http.impl.engine.ws

import java.net.{ InetAddress, InetSocketAddress }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class WebSocketConnectingCoordinatorSpec extends AnyWordSpec with Matchers {
  // An address value only: neither resolve the label nor open a connection.
  private def peer(label: String, lastOctet: Byte, port: Int): InetSocketAddress =
    new InetSocketAddress(InetAddress.getByAddress(label, Array[Byte](127, 0, 0, lastOctet)), port)

  "WebSocket opening admission" should {
    "admit the next alias when the first opening finishes, independently of its open lifetime" in {
      val coordinator = new WebSocketConnectingCoordinator
      val first = coordinator.acquire(peer("first.test", 1, 8080))
      val alias = coordinator.acquire(peer("alias.test", 1, 8080))
      val third = coordinator.acquire(peer("third.test", 1, 8080))
      val otherAddress = coordinator.acquire(peer("other.test", 2, 8080))
      val otherPort = coordinator.acquire(peer("first.test", 1, 8081))

      first.mayStart.value shouldBe Some(Success(true))
      alias.mayStart.isCompleted shouldBe false
      third.mayStart.isCompleted shouldBe false
      otherAddress.mayStart.value shouldBe Some(Success(true))
      otherPort.mayStart.value shouldBe Some(Success(true))

      first.openingFinished()
      alias.mayStart.value shouldBe Some(Success(true))
      first.openingFinished() // A later transport notification does not affect the new owner.
      third.mayStart.isCompleted shouldBe false
      alias.openingFinished()
      third.mayStart.value shouldBe Some(Success(true))

      third.openingFinished()
      otherAddress.openingFinished()
      otherPort.openingFinished()
      val reused = coordinator.acquire(peer("again.test", 1, 8080))
      reused.mayStart.value shouldBe Some(Success(true))
      reused.openingFinished()
    }

    "withdraw a queued opening before it owns permission to dial" in {
      val coordinator = new WebSocketConnectingCoordinator
      val first = coordinator.acquire(peer("first.test", 1, 8080))
      val queued = coordinator.acquire(peer("queued.test", 1, 8080))
      val next = coordinator.acquire(peer("next.test", 1, 8080))

      queued.withdrawWhileWaiting() shouldBe true
      queued.mayStart.value shouldBe Some(Success(false))
      next.mayStart.isCompleted shouldBe false

      first.openingFinished()
      next.mayStart.value shouldBe Some(Success(true))
      next.openingFinished()
    }
  }
}
