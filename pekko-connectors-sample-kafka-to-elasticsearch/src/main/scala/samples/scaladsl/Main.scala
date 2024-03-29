/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package samples.scaladsl

// #imports
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.kafka._
import org.apache.pekko.kafka.scaladsl.{ Committer, Consumer }
import org.apache.pekko.stream.connectors.elasticsearch.ElasticsearchParams
import org.apache.pekko.stream.connectors.elasticsearch.ElasticsearchWriteSettings
import org.apache.pekko.stream.connectors.elasticsearch.WriteMessage
import org.apache.pekko.stream.connectors.elasticsearch.scaladsl.ElasticsearchFlow
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.{ Done, NotUsed }
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
// #imports

object Main extends App with Helper {

  import JsonFormats._

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "KafkaToElasticSearch")
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  val topic = "movies-to-elasticsearch"
  private val groupId = "docs-group"

  // #es-setup
  val indexName = "movies"
  // #es-setup

  // #kafka-setup
  // configure Kafka consumer (1)
  val kafkaConsumerSettings = ConsumerSettings(actorSystem.toClassic, new IntegerDeserializer, new StringDeserializer)
    .withBootstrapServers(kafkaBootstrapServers)
    .withGroupId(groupId)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    .withStopTimeout(0.seconds)
  // #kafka-setup

  private def readFromKafkaWriteToElasticsearch() = {
    // #flow
    val control: Consumer.DrainingControl[Done] = Consumer
      .sourceWithOffsetContext(kafkaConsumerSettings, Subscriptions.topics(topic)) // (5)
      .map { consumerRecord => // (6)
        val movie = consumerRecord.value().parseJson.convertTo[Movie]
        WriteMessage.createUpsertMessage(movie.id.toString, movie)
      }
      .via(ElasticsearchFlow.createWithContext(
        ElasticsearchParams.V7(indexName), ElasticsearchWriteSettings(connectionSettings))) // (7)
      .map { writeResult => // (8)
        writeResult.error.foreach { errorJson =>
          throw new RuntimeException(s"Elasticsearch update failed ${writeResult.errorReason.getOrElse(errorJson)}")
        }
        NotUsed
      }
      .via(Committer.flowWithOffsetContext(CommitterSettings(actorSystem.toClassic))) // (9)
      .toMat(Sink.ignore)(Consumer.DrainingControl.apply) // (10)
      .run()
    // #flow
    control
  }

  val movies = List(Movie(23, "Psycho"), Movie(423, "Citizen Kane"))
  val writing: Future[Done] = writeToKafka(topic, movies)
  Await.result(writing, 10.seconds)

  val control = readFromKafkaWriteToElasticsearch()
  // Let the read/write stream run a bit
  Thread.sleep(5.seconds.toMillis)
  val copyingFinished = control.drainAndShutdown()
  Await.result(copyingFinished, 10.seconds)

  for {
    read <- readFromElasticsearch(indexName)
  } {
    read.foreach(m => println(s"read $m"))
    stopContainers()
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 10.seconds)
  }
}
