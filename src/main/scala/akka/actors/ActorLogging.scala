package akka.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLogging extends App {

  class SimpleActorWithExplicitLogging extends Actor {
    val logger = Logging(context.system, this)

    /**
     * levels of logging
     * 1- DEBUG
     * 2- INFO
     * 3- WARNING
     * 4- ERROR
     */
    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  class ImplicitActorLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(msg.toString) //we get access to log
    }
  }

  val guardian = ActorSystem("guardian")
  val actorLogging = guardian.actorOf(Props[SimpleActorWithExplicitLogging],"simpleactor")
  val implicitActorLogging = guardian.actorOf(Props[ImplicitActorLogging], "implicitactorlogging")

  actorLogging ! "helllowordl"
  implicitActorLogging ! "logging with implicit"

  Thread.sleep(200)
  guardian.terminate()
}
