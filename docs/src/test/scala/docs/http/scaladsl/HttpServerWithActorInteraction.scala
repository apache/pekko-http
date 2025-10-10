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

package docs.http.scaladsl

import org.apache.pekko
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorRef, ActorSystem }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.server.Directives._
import pekko.util.Timeout
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn

object HttpServerWithActorInteraction {

  object Auction {

    sealed trait Message

    case class Bid(userId: String, offer: Int) extends Message

    case class GetBids(replyTo: ActorRef[Bids]) extends Message

    case class Bids(bids: List[Bid])

    def apply: Behaviors.Receive[Message] = apply(List.empty)

    def apply(bids: List[Bid]): Behaviors.Receive[Message] = Behaviors.receive {
      case (ctx, bid @ Bid(userId, offer)) =>
        ctx.log.info(s"Bid complete: $userId, $offer")
        apply(bids :+ bid)
      case (_, GetBids(replyTo)) =>
        replyTo ! Bids(bids)
        Behaviors.same
    }

  }

  // these are from spray-json
  implicit val bidFormat: RootJsonFormat[Auction.Bid] = jsonFormat2(Auction.Bid.apply)
  implicit val bidsFormat: RootJsonFormat[Auction.Bids] = jsonFormat1(Auction.Bids.apply)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Auction.Message] = ActorSystem(Auction.apply, "auction")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContext = system.executionContext

    val auction: ActorRef[Auction.Message] = system
    import Auction._

    val route =
      path("auction") {
        concat(
          put {
            parameters("bid".as[Int], "user") { (bid, user) =>
              // place a bid, fire-and-forget
              auction ! Bid(user, bid)
              complete(StatusCodes.Accepted, "bid placed")
            }
          },
          get {
            implicit val timeout: Timeout = 5.seconds

            // query the actor for the current auction state
            val bids: Future[Bids] = auction.ask(GetBids(_))
            complete(bids)
          })
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}
