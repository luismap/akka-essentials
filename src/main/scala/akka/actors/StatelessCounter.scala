package akka.actors

import akka.actor.{Actor, ActorSystem, Props}

object StatelessCounter extends App {

  /**
   * actor that
   *  - increments
   *  - decrements
   *  - prints
   */


  class StatelessCounterActor extends Actor {
    import StatelessCounterActor._

    override def receive: Receive = handleMessage(0)
    def handleMessage(qty: Int): Receive = {
      case Increments(inc) => println(s"[increment] $inc"); context.become(handleMessage(inc + qty))
      case Decrement(dec) =>  println(s"[decrement] $dec"); context.become(handleMessage(qty - dec))
      case PrintIt => println(s"[total] $qty")
    }
  }

  object StatelessCounterActor {
    sealed trait CounterMessage
    case class Increments(qty: Int) extends CounterMessage
    case class Decrement(qty: Int) extends CounterMessage
    object PrintIt extends CounterMessage

    //def props(total: Int) = Props(new StatelessCounterActor(total))
  }

  val system = ActorSystem("counterManager")
  val counter = system.actorOf(Props[StatelessCounterActor])

  import StatelessCounterActor._

  counter ! Increments(2)
  counter ! Decrement(3)
  counter ! PrintIt

  system.terminate()


}
