package akka.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object AkkaConfig extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /**
   * inline config
   */
  val config =
    """
      | akka {
      |   loglevel = "DEBUG"
      |}
      |""".stripMargin

  val inlineConfig = ConfigFactory.parseString(config)
  val inlineGuardian = ActorSystem("guardian", ConfigFactory.load(inlineConfig))
  val simpleActor = inlineGuardian.actorOf(Props[SimpleActor])

  simpleActor ! "testing inline config actor"

  /**
   * default config,
   *  - akka looks for a config file in resources/application.conf
   */

  val defaultGuardian = ActorSystem("defaultGuardian")
  val defaultActor = defaultGuardian.actorOf(Props[SimpleActor])
  defaultActor ! "default config actor"

  /**
   * in same file config, using namespaces
   */
  val myConfig = ConfigFactory.load().getConfig("myConfig")
  val namespaceConfigGuardian = ActorSystem("defaultGuardian", myConfig)
  val namespaceConfigActor = namespaceConfigGuardian.actorOf(Props[SimpleActor])
  namespaceConfigActor ! "namespace config actor"


  /**
   * config in different files
   */
  val differentFileConfig = ConfigFactory.load("secret-configs/secret_akka.conf")
  println(s"[secret loglevel] ${differentFileConfig.getString("akka.loglevel")}")

  /**
   * different formats
   * json, properties files
   */
  val diffFormatsConfig = ConfigFactory.load("json/json_config.json")
  println(s"[json loglevel] ${diffFormatsConfig.getString("akka.loglevel")}")
  println(s"[json property] ${diffFormatsConfig.getString("propertyOne")}")

  val diffPropertiesConfig = ConfigFactory.load("props/akka_props.properties")
  println(s"[property loglevel] ${diffPropertiesConfig.getString("akka.loglevel")}")
  println(s"[property property] ${diffPropertiesConfig.getString("my.propertyOne")}")


  Thread.sleep(200)
  inlineGuardian.terminate()
  defaultGuardian.terminate()
  namespaceConfigGuardian.terminate()
}
