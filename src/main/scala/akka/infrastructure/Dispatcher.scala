package akka.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.infrastructure.Dispatcher.Counter.{Decrement, Increment}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Random

object Dispatcher extends App {

  object Counter {
    trait CounterMsg

    case class Increment(value: Int) extends CounterMsg

    case class Decrement(value: Int) extends CounterMsg

    case class Buffer(number: Int) extends CounterMsg
  }

  class Counter extends Actor with ActorLogging {

    import Counter._

    override def receive: Receive = countHandler(0)

    def countHandler(count: Int): Receive = {
      case Increment(value) =>
        val newCnt = value + count
        log.info(s"[${self.path}] INC ${newCnt}")
        context.become(countHandler(newCnt))
      case Decrement(value) =>
        val newCnt = count - value
        log.info(s"[${self.path}] DEC $newCnt")
        context.become(countHandler(newCnt))
      case Buffer(number) =>
        log.info(s"processed [$count] message [$number]")
        context.become(countHandler(count + 1))
    }
  }



  val systemGuardian = ActorSystem("counterGuardian")
  //using a dispatcher, decide on actors behaviour explicitly
  //load the dispatcher config progammatically
  val counter = for (i <- 0 to 10) yield systemGuardian.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter$i")

  val rnd = Random

  implicit val defaultContextDispatcher = systemGuardian.dispatcher

  import Counter.Buffer

  systemGuardian.scheduler.scheduleOnce(0 millis) {
    for (msg <- 0 to 1000) {
      counter(rnd.nextInt(10)) ! Buffer(msg)
    }

  }

  //load dispatcher config from
  val actorWithDispatcher = systemGuardian.actorOf(Props[Counter], "actorWithDispather")

  /**
   * dispatchers implements executioncontext trait
   */
  class SimulateDBActor extends Actor with ActorLogging {
    //implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")
    override def receive: Receive = {
      case msg => Future {
        Thread.sleep(1000)
        log.info(s"[$msg]")
      }
    }
  }

  val simulateDBActorWithDefDis = systemGuardian.actorOf(Props[SimulateDBActor],"simulateDBactor")
  val actorWithDefDispatcher = systemGuardian.actorOf(Props[Counter], "actorWithDefDispatcher")

  systemGuardian.scheduler.scheduleOnce(6 seconds){
    for (msg <- 0 to 100)
      simulateDBActorWithDefDis ! msg

    //the above will block this, they use same dispatcher
    //to fix, checkSimulateDBActor
    for (msg <- 0 to 100)
      actorWithDefDispatcher ! Buffer(msg)
  }


  systemGuardian.scheduler.scheduleOnce(12 seconds) {
    systemGuardian.terminate()
  }
}
