package com.cjemison.akkaHttp;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
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
import akka.persistence.AbstractPersistentActor;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

public class App extends AllDirectives {
  private final ActorRef exampleActor;
  private final MessageDispatcher messageDispatcher;

  public App(final ActorSystem system, final String dispatcherName) {
    this.exampleActor = system.actorOf(ExampleActor.props());
    this.messageDispatcher = system.dispatchers().lookup(dispatcherName);
  }

  public static void main(String[] args) throws Exception {
    final ActorSystem system = ActorSystem.create("routes");
    final Http http = Http.get(system);
    final ActorMaterializer materializer = ActorMaterializer.create(system);

    final App app = new App(system, "akka.actor.my-blocking-dispatcher");

    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow
          (system, materializer);
    final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
          ConnectHttp.toHost("localhost", 8080), materializer);

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding.thenCompose(ServerBinding::unbind) // trigger unbinding from the port
          .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  public Route createRoute() {
    return route(extractRequest(req -> completeWithFuture(CompletableFuture.supplyAsync(() -> {
      exampleActor.tell(UUID.randomUUID().toString(), ActorRef.noSender());
      return HttpResponse.create()
            .withStatus(200).withEntity(ContentTypes
                  .APPLICATION_JSON, "{}");
    }, messageDispatcher))));
  }

  public static class ExampleActor extends AbstractLoggingActor {

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
          log().info("here");
        }
      }).build();
    }
  }

  public static class PersistenceActor extends AbstractPersistentActor {

    @Override
    public Receive createReceiveRecover() {
      return null;
    }

    @Override
    public Receive createReceive() {
      return null;
    }

    @Override
    public String persistenceId() {
      return null;
    }
  }
}
