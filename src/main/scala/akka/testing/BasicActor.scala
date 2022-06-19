package akka.testing

import akka.actor.Actor

import scala.util.Random

class BasicActor extends Actor {
  override def receive: Receive = {
    case msg => sender() ! msg
  }

}

class NoReplyActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}

class LabActor extends Actor {
  val rnd = new Random()
  override def receive: Receive = {
    case "greeting" => if (rnd.nextBoolean()) sender() ! "hi" else sender() ! "hello"
    case "tech stack" =>
      sender() ! "scala"
      sender() ! "python"
    case msg: String => sender() ! msg.toUpperCase
  }
}