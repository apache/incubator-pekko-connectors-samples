/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package samples

import java.util.concurrent.TimeUnit

import org.apache.pekko.Done
import org.apache.pekko.actor.{ CoordinatedShutdown, Cancellable }
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.http.scaladsl._
import org.apache.pekko.http.scaladsl.model.StatusCodes._
import org.apache.pekko.http.scaladsl.model.headers.Accept
import org.apache.pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges }
import org.apache.pekko.kafka.scaladsl.{ Consumer, Producer }
import org.apache.pekko.kafka.{ ConsumerSettings, ProducerSettings, Subscriptions }
import org.apache.pekko.stream.connectors.csv.scaladsl.{ CsvParsing, CsvToMap }
import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }
import org.apache.pekko.util.ByteString
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import org.testcontainers.containers.KafkaContainer
import spray.json.{ DefaultJsonProtocol, JsValue, JsonWriter }

import scala.concurrent.Future
import scala.concurrent.duration._

object Main
  extends App
    with DefaultJsonProtocol {

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "pekko-connectors-samples")

  import actorSystem.executionContext

  val httpRequest = HttpRequest(uri = "https://www.nasdaq.com/screening/companies-by-name.aspx?exchange=NASDAQ&render=download")
    .withHeaders(Accept(MediaRanges.`text/*`))

  def extractEntityData(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(OK, _, entity, _) => entity.dataBytes
      case notOkResponse =>
        Source.failed(new RuntimeException(s"illegal response $notOkResponse"))
    }

  def cleanseCsvData(csvData: Map[String, ByteString]): Map[String, String] =
    csvData
      .filterNot { case (key, _) => key.isEmpty }
      .view
      .mapValues(_.utf8String)
      .toMap

  def toJson(map: Map[String, String])(
    implicit jsWriter: JsonWriter[Map[String, String]]): JsValue = jsWriter.write(map)

  val kafkaBroker: KafkaContainer = new KafkaContainer()
  kafkaBroker.start()

  private val bootstrapServers: String = kafkaBroker.getBootstrapServers()

  val kafkaProducerSettings = ProducerSettings(actorSystem.toClassic, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)

  val (ticks, future): (Cancellable, Future[Done]) =
    Source
      .tick(1.seconds, 7.seconds, httpRequest) //: HttpRequest
      .mapAsync(1)(Http()(actorSystem.toClassic).singleRequest(_)) //: HttpResponse
      .flatMapConcat(extractEntityData) //: ByteString
      .via(CsvParsing.lineScanner()) //: List[ByteString]
      .via(CsvToMap.toMap()) //: Map[String, ByteString]
      .map(cleanseCsvData) //: Map[String, String]
      .map(toJson) //: JsValue
      .map(_.compactPrint) //: String (JSON formatted)
      .map { elem =>
        new ProducerRecord[String, String]("topic1", elem) //: Kafka ProducerRecord
      }
      .toMat(Producer.plainSink(kafkaProducerSettings))(Keep.both)
      .run()

  val cs: CoordinatedShutdown = CoordinatedShutdown(actorSystem)
  cs.addTask(CoordinatedShutdown.PhaseServiceStop, "shut-down-client-http-pool")( () =>
    Http()(actorSystem.toClassic).shutdownAllConnectionPools().map(_ => Done)
  )

  val kafkaConsumerSettings = ConsumerSettings(actorSystem.toClassic, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId("topic1")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val control = Consumer
    .atMostOnceSource(kafkaConsumerSettings, Subscriptions.topics("topic1"))
    .map(_.value)
    .toMat(Sink.foreach(println))(Keep.both)
    .mapMaterializedValue(Consumer.DrainingControl.apply[Done])
    .run()

  TimeUnit.SECONDS.sleep(59)
  ticks.cancel()

  for {
    _ <- future
    _ <- control.drainAndShutdown()
  } {
    kafkaBroker.stop()
    cs.run(CoordinatedShutdown.UnknownReason)
  }
}
