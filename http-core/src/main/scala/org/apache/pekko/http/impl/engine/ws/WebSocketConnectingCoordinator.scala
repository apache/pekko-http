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

import java.net.{ Inet6Address, InetAddress, InetSocketAddress }

import scala.collection.mutable
import scala.concurrent.{ Future, Promise }

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API.
 *
 * Tracks dial admission for already resolved direct peers. The owner of the transport must
 * supply the end of the opening phase; this coordinator neither opens nor closes a connection.
 */
@InternalApi
private[http] final class WebSocketConnectingCoordinator {
  import WebSocketConnectingCoordinator.Peer

  private final class Lane(var owner: Lease) {
    val waiting = mutable.LinkedHashSet.empty[Lease]
  }

  final class Lease private[WebSocketConnectingCoordinator] (
      private[WebSocketConnectingCoordinator] val peer: Peer) {
    private[WebSocketConnectingCoordinator] val permit = Promise[Boolean]()

    /** True when admitted, false if withdrawn before admission. */
    def mayStart: Future[Boolean] = permit.future

    /** Withdraws only a queued lease. A granted lease still needs an opening completion. */
    def withdrawWhileWaiting(): Boolean = withdraw(this)

    /**
     * Releases the opening slot after a validated upgrade, or after the transport owner
     * has established termination or that the dial never started. Repeated calls are harmless.
     */
    def openingFinished(): Unit = release(this)
  }

  private val lanes = mutable.Map.empty[Peer, Lane]

  def acquire(resolvedPeer: InetSocketAddress): Lease = {
    require(!resolvedPeer.isUnresolved, "The direct peer must be resolved")
    val address = resolvedPeer.getAddress
    val scope = address match {
      case scoped: Inet6Address => scoped.getScopeId
      case _                    => 0
    }
    val peer = Peer(address, scope, resolvedPeer.getPort)
    val lease = new Lease(peer)
    val granted = synchronized {
      lanes.get(peer) match {
        case Some(lane) =>
          lane.waiting += lease
          false
        case None =>
          lanes.put(peer, new Lane(lease))
          true
      }
    }
    if (granted) lease.permit.trySuccess(true)
    lease
  }

  private def withdraw(lease: Lease): Boolean = {
    val removed = synchronized {
      lanes.get(lease.peer).exists(_.waiting.remove(lease))
    }
    if (removed) lease.permit.trySuccess(false)
    removed
  }

  private def release(lease: Lease): Unit = {
    val next = synchronized {
      lanes.get(lease.peer).filter(_.owner eq lease).flatMap { lane =>
        lane.waiting.headOption match {
          case Some(waiter) =>
            lane.waiting -= waiter
            lane.owner = waiter
            Some(waiter)
          case None =>
            lanes.remove(lease.peer)
            None
        }
      }
    }
    next.foreach(_.permit.trySuccess(true))
  }
}

/** INTERNAL API */
@InternalApi
private[http] object WebSocketConnectingCoordinator {
  private[WebSocketConnectingCoordinator] final case class Peer(address: InetAddress, scope: Int, port: Int)
}
