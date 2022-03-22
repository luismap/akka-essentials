package akka.faulttolerance

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}
import akka.dispatch.sysmsg.Terminate

object StartingStoppingActors extends App {

  object Parent {
    case class Start(child: String)
    case class Stop(child: String)

  }
  class Parent extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = handlingChildren(Map.empty)

    def handlingChildren(children: Map[String, ActorRef]): Receive = {
      case Start(child) =>
        log.info(s"[creating child] $child")
        val tmpChildren = children + (child -> context.actorOf(Props[Children], child))
        context.become(handlingChildren(tmpChildren))

      case Stop(child) =>
        //non-blocking stop
        children.get(child).foreach(childRef => context.stop(childRef))
        log.info(s"[stopping child] $child")
        context.become(handlingChildren(children - child))

      case Stop =>
        log.info(s"[stoping ${self.path.name}]")
        context.stop(self)
    }
  }

  class Children extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"[children got message] $message")
    }
  }

  import Parent._
  val guardian = ActorSystem("guardian")
  val parent = guardian.actorOf(Props[Parent], "parent")
  parent ! Start("childOne")
  val child = guardian.actorSelection("/user/parent/childOne")
  child ! "hello"
  parent ! Stop("childOne")
  child ! "hello"
  for (_ <- 0 to 10 ) child ! "are you awake"

  parent ! Start("childTwo")
  val childTwo = guardian.actorSelection("/user/parent/childTwo")
  parent ! Stop


  for (_ <- 0 to 10 ) childTwo ! "are you awake"

  val looseChild = guardian.actorOf(Props[Children], "looseChild")
  looseChild ! PoisonPill //terminate the actor

  val killActor = guardian.actorOf(Props[Children])
  killActor ! Kill //kills directly

  killActor ! "ahllele"


  /**
   * death watch,
   *  message that controls the live of a child
   */
  class Watcher extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = {
      case Start(name) =>
        val child = context.actorOf(Props[Children], name)
        log.info(s"[watcher started following] ${child.path.name}")
        context.watch(child)
      case Terminated(ref) =>
        log.info(s"[watcher received terminated] for ${ref.path.name}")
    }
  }

  val watcher = guardian.actorOf(Props[Watcher], "watcher")
  watcher ! Start("onechild")
  val child1 = guardian.actorSelection("/user/watcher/onechild")
  Thread.sleep(500)
  child1 ! PoisonPill

  Thread.sleep(500)
  guardian.terminate()
}
