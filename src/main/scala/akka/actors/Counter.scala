package akka.actors

import akka.actor.{Actor, ActorSystem, Props}

object Counter extends App {

  /**
   * actor that
   *  - increments
   *  - decrements
   *  - prints
   */

  sealed trait CounterMessage
  case class Increments(qty: Int ) extends CounterMessage
  case class Decrement(qty: Int ) extends CounterMessage
  object PrintIt extends CounterMessage

  case class CounterActor(var total: Int = 0) extends Actor {
    override def receive: Receive = {
      case msg @ Increments(qty) => total += qty; handleMsg(msg)
      case msg @ Decrement(qty) => total += qty; handleMsg(msg)
      case msg @ PrintIt => handleMsg(msg)

    }

    def handleMsg(message: CounterMessage) = message match {
      case Increments(qty) => println(s"[incremeting] $qty")
      case Decrement(qty) => println(s"[decrementing] $qty")
      case PrintIt => println(s"[total] $total")
    }

    }

  object CounterActor {
    def props(total: Int) = Props(new CounterActor(total))
  }

  val system = ActorSystem("counterManager")
  val counter = system.actorOf(Props(new CounterActor()))
  val counter2 = system.actorOf(CounterActor.props(20))

  counter ! Increments(2)
  counter ! Decrement(3)
  counter ! PrintIt
  counter2 ! PrintIt

  system.terminate()




}
