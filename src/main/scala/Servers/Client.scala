package Servers

import Models.Stats
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto.exportDecoder
import io.circe.jawn.decode
import org.apache.kafka.clients.consumer.ConsumerConfig._
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer

import java.util.Properties
import java.time.Duration
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable

object Client extends App {

  val conf: Config = ConfigFactory.load("application.conf")
  val topic: String = conf.getString("topic")
  val bootstrapServer: String = conf.getString("bootstrapServer")
  val groupId: String = conf.getString("groupId")

  private def createCons: Properties = {
    val consumerProperties = new Properties()
    consumerProperties.setProperty(BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
    consumerProperties.setProperty(GROUP_ID_CONFIG, groupId)
    consumerProperties.setProperty(KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    consumerProperties.setProperty(VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    consumerProperties.setProperty(AUTO_OFFSET_RESET_CONFIG, "earliest")
    consumerProperties.setProperty(ENABLE_AUTO_COMMIT_CONFIG, "false")
    consumerProperties
  }

  var cash: mutable.Map[String, mutable.Map[Int, Long]] =
    mutable.Map("state" -> mutable.Map(1 -> 0, 2 -> 0), "center" -> mutable.Map(1 -> 0, 2 -> 0))

  val consumer = new KafkaConsumer[String, String](createCons)
  consumer.subscribe(List(topic).asJava)
  while (true) {
    val polledRecords: ConsumerRecords[String, String] = consumer.poll(Duration.ofSeconds(1))
    if (!polledRecords.isEmpty) {
      val recordIterator = polledRecords.iterator()
      while (recordIterator.hasNext) {
        val record = recordIterator.next()
        println(s"| ${record.value()} |")
        val newStats = decode[Stats](record.value())
        newStats match {
          case Right(value) => {
            val node = value.node
            val sites = value.colSites
            cash = cash.map(x => (x._1, x._2.clone().addOne(node, sites(x._1))))
          }
        }
        println(cash)
        cash.foreach(x => println(s"Site: ${x._1} General stat: ${x._2.maxBy { case (k, v) => v }}"))
      }
      consumer.commitAsync()
    }
  }
}
