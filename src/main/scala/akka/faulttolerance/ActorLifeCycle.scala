package akka.faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.faulttolerance.ActorLifeCycle.Parent.Fail

object ActorLifeCycle extends App {
  object StartChild

  class LifeCycleActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info(s"[preStart] ${self.path.name}")

    override def postStop(): Unit = log.info(s"[postStop] ${self.path.name}")

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifeCycleActor], "child")
    }
  }

  val system = ActorSystem("system")
  val lifeCycleActor = system.actorOf(Props[LifeCycleActor], "parent")
  lifeCycleActor ! StartChild
  lifeCycleActor ! PoisonPill

  Thread.sleep(1000)


  object Parent {
    object Fail

    object FailChild

    object Check

    object CheckChild
  }

  class Parent extends Actor with ActorLogging {

    import Parent._

    val child = context.actorOf(Props[Child], "child")

    override def receive: Receive = {
      case Fail =>
        child ! FailChild
      case Check =>
        child ! CheckChild
    }
  }

  class Child extends Actor with ActorLogging {

    import Parent._

    override def preStart(): Unit = log.info(s"[child] prestarted")

    override def postStop(): Unit = log.info(s"[child] postStopped")

    /**
     * in the actor lifecycle, if an actor fails,
     * the system will try to restart it
     * @param reason
     * @param message
     */
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info(s"[child] is preRestarting")
      log.info(s"[child] because ${reason.getMessage}")
    }

    override def postRestart(reason: Throwable): Unit =
      log.info(s"[child] is postRestarting")

    override def receive: Receive = {
      case FailChild =>
        log.warning("child will fail")
        throw new RuntimeException("Child Failed")
      case CheckChild =>
        log.info("child is alive")
    }
  }

  import Parent._

  val parent = system.actorOf(Props[Parent])
  parent ! Fail
  parent ! Check

  Thread.sleep(500)
  system.terminate()
}
