/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package playground

import org.apache.pekko.actor.{ ActorSystem, Terminated }
import org.apache.pekko.event.Logging
import org.apache.pekko.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import org.apache.pekko.http.scaladsl.model.{ ContentTypes, HttpEntity }
import org.apache.pekko.http.scaladsl.server.{ HttpApp, Route }
import org.apache.pekko.http.scaladsl.settings.ServerSettings
import org.apache.pekko.stream.scaladsl.{ Flow, GraphDSL, Sink, Source }
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.{ Done, NotUsed }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Success

class WebServer extends HttpApp {
  implicit val theSystem: ActorSystem = ActorSystem(Logging.simpleName(this).replaceAll("\\$", ""))
  implicit val executionContext: ExecutionContext = theSystem.dispatcher

  private val shutdownPromise = Promise[Done]

  /**
   * Override to do something more interesting on Web socket messages
   * https://pekko.apache.org/docs/pekko-http/current/scala/http/websocket-support.html#routing-support
   */
  def websocket: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        println(s"Web server received web socket message: $tm")
        TextMessage(
          Source
            .single("Hello ")
            .concat(tm.textStream)
            .concat(Source.single("!"))) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

  /**
   * Sends out messages on the websocket.
   */
  def outgoing: Flow[Message, Message, NotUsed] = {
    val routingGraph: Flow[Message, Message, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
      val in = b.add(Sink.ignore)
      val out = b.add(Source.tick(2.seconds, 10.seconds, TextMessage("Tick")))
      FlowShape(in.in, out.out)
    })
    Flow[Message].via(routingGraph)
  }

  /**
   * @see https://pekko.apache.org/docs/pekko-http/current/scala/http/routing-dsl/overview.html
   *      https://pekko.apache.org/docs/pekko-http/current/scala/http/routing-dsl/directives/alphabetically.html
   */
  override def routes: Route =
    pathSingleSlash {
      complete {
        println("Web server received GET /")
        HttpEntity(ContentTypes.`text/html(UTF-8)`, "<html><body>Welcome to the playground!</body></html>")
      }
    } ~
    path("hello") {
      get { ctx =>
        ctx.complete {
          println(s"Web server received ${ctx.request}")
          HttpEntity(ContentTypes.`application/json`, """{ msg: "Hi!" }""")
        }
      }
    } ~
    pathPrefix("webSocket") {
      path("ping") {
        // connect e.g. with Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8080/webSocket/ping"))
        println("Web server received webSocket/ping connect")
        handleWebSocketMessages(websocket)
      } // ~
      //        path("outgoing") {
      //          handleWebSocketMessages(outgoing)
      //        }
    }

  override protected def postHttpBindingFailure(cause: Throwable): Unit =
    println(s"The server could not be started due to $cause")

  def start(host: String = "localhost", port: Int = 8080): Future[Done] = {
    val settings = ServerSettings(theSystem.settings.config)
    Future {
      startServer(host, port, settings, theSystem)
    }.map(_ => Done)
  }

  override protected def waitForShutdownSignal(system: ActorSystem)(implicit ec: ExecutionContext): Future[Done] =
    shutdownPromise.future

  def stop(): Future[Terminated] = {
    shutdownPromise.tryComplete(Success(Done))
    theSystem.terminate()
  }
}

object WebServer extends WebServer
