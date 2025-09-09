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

package docs.http.javadsl;

// #actor-interaction

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.apache.pekko.stream.javadsl.Flow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.actor.typed.javadsl.AskPattern.ask;

public class HttpServerActorInteractionExample extends AllDirectives {

  private final ActorSystem<Auction.Message> system;
  private final ActorRef<Auction.Message> auction;

  public static void main(String[] args) throws Exception {
    // boot up server using the route as defined below
    ActorSystem<Auction.Message> system = ActorSystem.create(Auction.create(), "routes");

    final Http http = Http.get(system);

    // In order to access all directives we need an instance where the routes are define.
    HttpServerActorInteractionExample app = new HttpServerActorInteractionExample(system);

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding
        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
        .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  private HttpServerActorInteractionExample(final ActorSystem<Auction.Message> system) {
    this.system = system;
    this.auction = system;
  }

  private Route createRoute() {
    return concat(
        path(
            "auction",
            () ->
                concat(
                    put(
                        () ->
                            parameter(
                                StringUnmarshallers.INTEGER,
                                "bid",
                                bid ->
                                    parameter(
                                        "user",
                                        user -> {
                                          // place a bid, fire-and-forget
                                          auction.tell(new Auction.Bid(user, bid));
                                          return complete(StatusCodes.ACCEPTED, "bid placed");
                                        }))),
                    get(
                        () -> {
                          // query the actor for the current auction state
                          CompletionStage<Auction.Bids> bids =
                              ask(
                                  auction,
                                  Auction.GetBids::new,
                                  Duration.ofSeconds(5),
                                  system.scheduler());
                          return completeOKWithFuture(bids, Jackson.marshaller());
                        }))));
  }

  static class Auction extends AbstractBehavior<Auction.Message> {
    interface Message {}

    static class Bid implements Message {
      public final String userId;
      public final int offer;

      Bid(String userId, int offer) {
        this.userId = userId;
        this.offer = offer;
      }
    }

    static class GetBids implements Message {
      final ActorRef<Bids> replyTo;

      GetBids(ActorRef<Bids> replyTo) {
        this.replyTo = replyTo;
      }
    }

    static class Bids {
      public final List<Bid> bids;

      Bids(List<Bid> bids) {
        this.bids = bids;
      }
    }

    public Auction(ActorContext<Message> context) {
      super(context);
    }

    private List<Bid> bids = new ArrayList<>();

    public static Behavior<Message> create() {
      return Behaviors.setup(Auction::new);
    }

    @Override
    public Receive<Message> createReceive() {
      return newReceiveBuilder()
          .onMessage(Bid.class, this::onBid)
          .onMessage(GetBids.class, this::onGetBids)
          .build();
    }

    private Behavior<Message> onBid(Bid bid) {
      bids.add(bid);
      getContext().getLog().info("Bid complete: {}, {}", bid.userId, bid.offer);
      return this;
    }

    private Behavior<Message> onGetBids(GetBids getBids) {
      getBids.replyTo.tell(new Bids(bids));
      return this;
    }
  }
}
// #actor-interaction
