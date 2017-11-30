package com.cjemison.akkaHttp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

public class App extends AllDirectives {
  public static final ActorSystem system = ActorSystem.create("routes");
  private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
  private final ActorRef exampleActor;

  public App(final ActorSystem system) {
    this.exampleActor = system.actorOf(ExampleActor.props());
  }

  public static void main(String[] args) throws Exception {
    final Http http = Http.get(system);
    final ActorMaterializer materializer = ActorMaterializer.create(system);

    final App app = new App(system);

    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow
          (system, materializer);
    final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
          ConnectHttp.toHost("localhost", 8080), materializer);

    LOGGER.info("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding.thenCompose(ServerBinding::unbind) // trigger unbinding from the port
          .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  public Route createRoute() {
    return route(extractRequest(req -> {
      final MessageDispatcher dispatcher =
            system.dispatchers().lookup("akka.actor.my-blocking-dispatcher");
      return completeWithFuture(CompletableFuture.supplyAsync(() -> {
        exampleActor.tell(UUID.randomUUID().toString(), ActorRef.noSender());


        LOGGER.info("Sending Message: temp");
        return HttpResponse.create()
              .withStatus(200).withEntity(ContentTypes
                    .APPLICATION_JSON, "{}");
      }, dispatcher));
    }));
  }

  public static class ExampleActor extends AbstractActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleActor.class);

    public static Props props() {
      return Props.create(ExampleActor.class);
    }

    @Override
    public Receive createReceive() {
      return ReceiveBuilder.create().match(String.class, s -> {
        LOGGER.debug("Async Actor", s);
        double count = 0.0;
        while (count < 100000000) {
          count = count + .01;
        }
      }).build();
    }
  }
}
