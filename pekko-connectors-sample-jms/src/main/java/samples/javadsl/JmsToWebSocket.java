/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package samples.javadsl;

// #sample

import static org.apache.pekko.actor.typed.javadsl.Adapter.toClassic;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocketRequest;
import org.apache.pekko.http.javadsl.model.ws.WebSocketUpgradeResponse;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.connectors.jms.JmsConsumerSettings;
import org.apache.pekko.stream.connectors.jms.JmsProducerSettings;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsConsumer;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsConsumerControl;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsProducer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import playground.ActiveMqBroker;
import playground.WebServer;

import javax.jms.ConnectionFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import scala.concurrent.ExecutionContext;

// #sample

public class JmsToWebSocket {

  public static void main(String[] args) throws Exception {
    JmsToWebSocket me = new JmsToWebSocket();
    me.run();
  }

  private final ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "JmsToWebSocket");
  private final ExecutionContext ec = system.executionContext();

  private void enqueue(ConnectionFactory connectionFactory, String... msgs) {
    Sink<String, ?> jmsSink =
        JmsProducer.textSink(
            JmsProducerSettings.create(system, connectionFactory).withQueue("test"));
    Source.from(Arrays.asList(msgs)).runWith(jmsSink, system);
  }

  private void run() throws Exception {
    ActiveMqBroker activeMqBroker = new ActiveMqBroker();
    activeMqBroker.start();

    WebServer webserver = new WebServer();
    webserver.start("localhost", 8080);

    ConnectionFactory connectionFactory = activeMqBroker.createConnectionFactory();
    enqueue(connectionFactory, "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
    // #sample

    final Http http = Http.get(toClassic(system));

    Source<String, JmsConsumerControl> jmsSource = // (1)
        JmsConsumer.textSource(
            JmsConsumerSettings.create(system, connectionFactory)
                .withBufferSize(10)
                .withQueue("test"));

    Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>> webSocketFlow = // (2)
        http.webSocketClientFlow(WebSocketRequest.create("ws://localhost:8080/webSocket/ping"));

    int parallelism = 4;
    Pair<Pair<JmsConsumerControl, CompletionStage<WebSocketUpgradeResponse>>, CompletionStage<Done>>
        pair =
        jmsSource // : String
            .map(
                s -> {
                  Message msg = TextMessage.create(s);
                  return msg;
                }) // : Message           (3)
            .viaMat(webSocketFlow, Keep.both()) // : Message           (4)
            .mapAsync(parallelism, this::wsMessageToString) // : String            (5)
            .map(s -> "client received: " + s) // : String            (6)
            .toMat(Sink.foreach(System.out::println), Keep.both()) //                    (7)
            .run(system);
    // #sample
    JmsConsumerControl runningSource = pair.first().first();
    CompletionStage<WebSocketUpgradeResponse> wsUpgradeResponse = pair.first().second();
    CompletionStage<Done> streamCompletion = pair.second();

    wsUpgradeResponse
        .thenApply(
            upgrade -> {
              if (upgrade.response().status() == StatusCodes.SWITCHING_PROTOCOLS) {
                return "WebSocket established";
              } else {
                throw new RuntimeException("Connection failed: " + upgrade.response().status());
              }
            })
        .thenAccept(System.out::println);

    Thread.sleep(2 * 1000);
    runningSource.shutdown();
    streamCompletion.thenAccept(res -> system.terminate());
    system
        .getWhenTerminated()
        .thenCompose(
            t -> {
              webserver.stop();
              return activeMqBroker.stopCs(ec);
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  // #sample

  /**
   * Convert potentially chunked WebSocket Message to a string.
   */
  private CompletionStage<String> wsMessageToString(Message msg) {
    if (msg.isText()) {
      TextMessage tMsg = msg.asTextMessage();
      if (tMsg.isStrict()) {
        return CompletableFuture.completedFuture(tMsg.getStrictText());
      } else {
        CompletionStage<List<String>> strings =
            tMsg.getStreamedText().runWith(Sink.seq(), system);
        return strings.thenApply(list -> String.join("", list));
      }
    } else {
      return CompletableFuture.completedFuture(msg.toString());
    }
  }
  // #sample

}
