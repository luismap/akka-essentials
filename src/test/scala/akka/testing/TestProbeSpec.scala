package akka.testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}


class TestProbeSpec extends TestKit(ActorSystem("testprobekit"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {
  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "Master actor" should {
    import Master._
    "initialize a worker instance" in {
      val master = system.actorOf(Props[Master], "master")
      val slaveProbe = TestProbe("slaveProbe")
      master ! InitWorker(slaveProbe.ref)

      expectMsg(Initialized)

    }
    "send work to executor" in {
      val master = system.actorOf(Props[Master])
      val slaveProbe = TestProbe("slaveProbe")
      val task = "hello world"
      master ! InitWorker(slaveProbe.ref)
      expectMsg(Initialized)
      master ! Task(task)

      //testActor, reference create by TestKit
      //implicitSender, sends originator the testActor
      slaveProbe.expectMsg(DoWork(testActor, task))
      slaveProbe.reply(WorkComplete(testActor, 2))

      expectMsg(Reply(2))
    }

    "aggregate correctly more than one message" in {
      val master = system.actorOf(Props[Master])
      val slaveProbe = TestProbe("slaveProbe")
      val task = "hello world"
      master ! InitWorker(slaveProbe.ref)
      expectMsg(Initialized)

      master ! Task(task)
      master ! Task(task)

      slaveProbe.receiveWhile() {
        case DoWork(`testActor`, `task`) => slaveProbe.reply(WorkComplete(testActor, 2))
      }
      expectMsg(Reply(2))
      expectMsg(Reply(4))
    }


  }
}

object Master {
  sealed trait MasterMsg

  case class InitWorker(slave: ActorRef) extends MasterMsg

  case class DoWork(originator: ActorRef, work: String) extends MasterMsg

  case class Task(task: String) extends MasterMsg

  case class WorkComplete(originator: ActorRef, count: Int) extends MasterMsg

  case class Reply(count: Int) extends MasterMsg

  object Initialized extends MasterMsg
}

class Master extends Actor {

  import Master._

  override def receive: Receive = {
    case InitWorker(slave) =>
      sender() ! Initialized
      context.become(running(slave, 0))
    case _ =>
  }

  def running(ref: ActorRef, i: Int): Receive = {
    case Task(work) =>
      ref ! DoWork(sender(), work)
    case WorkComplete(originator, count) =>
      val total = i + count
      originator ! Reply(total)
      context.become(running(ref, total))
  }
}
