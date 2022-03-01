package akka.actors

import akka.actor.{Actor, ActorSystem, Props, actorRef2Scala}

object Actors extends App {

  /**
   * - system actors
   */
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  /**
   * - defining actors
   */

  class Wordcount extends Actor {
    var count = 0

    override def receive: PartialFunction[Any, Unit] = {
      case message: String => parseMessage(message)
      case msg => println(s"[message] could no decode $msg")
    }

    def parseMessage(msg: String) = {
      println(s"[received] $msg")
      count += msg.split(" ").length
      println(s"[updating count] $count")
    }
  }

  /**
   * instanciating the actor
   */

  val wordCounter = actorSystem.actorOf(Props[Wordcount], "WordCount")
  val anotherWordCounter = actorSystem.actorOf(Props[Wordcount], "WordCount2")

  /**
   * communicate with the actor
   */

  wordCounter ! "frist emss"
  wordCounter ! "helloasd awoer"
  wordCounter ! 29
  anotherWordCounter ! "the second wordcounter"

  /**
   * instaciating classe for actors
   */

  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case msg: String => msg match {
        case _ if msg == "show" => println(s"$name")
      }
      case _ => println(s"wrong type for person")
    }
  }
  object PersonBestPractice {
    def props(name: String) = Props(new PersonBestPractice(name))
  }
  class PersonBestPractice(name: String) extends Actor {
    override def receive: Receive = {
      case msg: String => msg match {
        case _ if msg == "show" => println(s"$name")
      }
      case _ => println(s"wrong type for person")
    }
  }
  val person = actorSystem.actorOf(Props(new Person("luis")))
  val personBestPractice = actorSystem.actorOf(PersonBestPractice.props("anna"))

  person ! "show"
  person ! 38

  personBestPractice ! "show"
  personBestPractice ! 38

  actorSystem.terminate()


}
