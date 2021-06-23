package Servers

import Models.Stats
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{concat, get, getFromFile, path}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import io.circe.generic.auto.exportEncoder
import io.circe.syntax._

import java.util.Properties
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Server2 {
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Server2")
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  val conf: Config = ConfigFactory.load("application.conf")
  val host: String = conf.getString("host")
  val port: Int = conf.getInt("port2")
  val topic: String = conf.getString("topic")
  val bootstrapServer: String = conf.getString("bootstrapServer")
  val sites: mutable.Map[String, Long] = mutable.Map("state" -> 0, "center" -> 0)
  val nodeNumber: Int = conf.getInt("node2")

  val state: Stats = Stats(nodeNumber, sites)
  private def createProducer: Properties = {
    val producerProperties = new Properties()
    producerProperties.setProperty(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
      bootstrapServer
    )
    producerProperties.setProperty(
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      classOf[StringSerializer].getName
    )
    producerProperties.setProperty(
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      classOf[StringSerializer].getName
    )
    producerProperties
  }

  val producer = new KafkaProducer[String, String](createProducer)

  def main(args: Array[String]): Unit = {
    val route = get {
      concat(
        path("state") {
          state.colSites("state") += 1
          if (state.colSites("state") % 5 == 0) {
            val json = state.asJson.noSpaces
            println(json)
            producer.send(new ProducerRecord[String, String](topic, json))
            producer.flush()
          }
          getFromFile("data/index.html")
        },
        path("center") {
          state.colSites("center") += 1
          if (state.colSites("center") % 5 == 0) {
            val json = state.asJson.noSpaces
            println(json)
            producer.send(new ProducerRecord[String, String](topic, json))
            producer.flush()
          }
          getFromFile("data/centerIndex.html")
        }
      )

    }

    val bindingFuture = Http().newServerAt(host, port).bind(route)
    println(s"Server online at http://${host}:${port}/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

}
