package akka.testing

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testing.SupervisionSpec.WordCounter.Report
import akka.testkit.{CallingThreadDispatcher, EventFilter, ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt


class SupervisionSpec extends TestKit(ActorSystem("supervisor"))
  with WordSpecLike
  with ImplicitSender
  with BeforeAndAfterAll {
  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  import SupervisionSpec._

  "A supervisor" should {
    "resume a child in case of minor failure" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      child ! "count some word"
      child ! Report
      expectMsg(3)
      child ! "lonnngg, suequencess of charraasdfasdfasfd" //trigger a failure
      child ! Report
      expectMsg(3)
    }

    "clear state if restart and actor" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      child ! "count some word"
      child ! Report
      expectMsg(3)
      child ! ""
      child ! Report
      expectMsg(0)
    }

    "stop actor in case of IllegalArgument" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! "count some word"
      child ! Report
      expectMsg(3)
      child ! "Akdfs"
      expectMsgPF(1 second) {
        case Terminated(ref) =>
          assert(ref == child)
      }

    }

    "escalate exception to parent" in {
      val supervisor = system.actorOf(Props[Supervisor], "parentsupervisor")
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! "count some word"
      child ! Report
      expectMsg(3)
      child ! 22
      val expectedMsg = expectMsgType[Terminated]
      assert(expectedMsg.actor == child)
    }

  }

  "A kinder supervisor" should {
    "no kill its children in case of restart or scalate" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "kindersupervisor")
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! "count some word"
      child ! Report
      expectMsg(3)
      child ! 45
      child ! Report
      expectMsg(0)
    }
  }

  "An all-for-one supervisor" should {
    "restart all of it children, if one fails" in {
      //calls need to be syncrhonize
      val supervisor = system.actorOf(
        Props[AllForOneSupervisor]
          .withDispatcher(CallingThreadDispatcher.Id)
        , "allforonesupervisor")

      supervisor ! Props[WordCounter].withDispatcher(CallingThreadDispatcher.Id)
      val child = expectMsgType[ActorRef]
      supervisor ! Props[WordCounter].withDispatcher(CallingThreadDispatcher.Id)
      val child2 = expectMsgType[ActorRef]

      child ! "count some word"
      child2 ! "counsd this"
      child2 ! Report
      expectMsg(2)
      EventFilter[NullPointerException]().intercept(
        child ! ""
      )

      child2 ! Report
      expectMsg(0)

    }

    "another way of sychronizing" in {
      val supervisor = TestActorRef[AllForOneSupervisor](Props[AllForOneSupervisor],"allforoneSync")

      supervisor ! Props[WordCounter].withDispatcher(CallingThreadDispatcher.Id)
      val child = expectMsgType[ActorRef]
      supervisor ! Props[WordCounter].withDispatcher(CallingThreadDispatcher.Id)
      val child2 = expectMsgType[ActorRef]

      child ! "count some word"
      child2 ! "counsd this"
      child2 ! Report
      expectMsg(2)
      EventFilter[NullPointerException]().intercept(
        child ! ""
      )

      child2 ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  class Supervisor extends Actor {
    //applying strategy to the children that failed
    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException => Restart //remove state, and restart actor
      case _: IllegalArgumentException => Stop //stops actor
      case _: RuntimeException => Resume //will keep the state of the actor
      case _: Exception => Escalate //stops children, and scalate
    }

    override def receive: Receive = {
      case props: Props =>
        val ref = context.actorOf(props)
        sender() ! ref
    }
  }

  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      //do nothing
    }
  }

  class AllForOneSupervisor extends Supervisor with ActorLogging {
    override def supervisorStrategy: SupervisorStrategy =
      AllForOneStrategy() {
        case _: NullPointerException =>
          log.info("[restarting]")
          Restart //remove state, and restart actor
        case _: IllegalArgumentException => Stop //stops actor
        case _: RuntimeException => Resume //will keep the state of the actor
        case _: Exception => Escalate //stops children, and scalate
      }

  }

  object WordCounter {
    object Report
  }

  class WordCounter extends Actor with ActorLogging {

    import WordCounter._

    var counts = 0

    override def receive: Receive = {
      case Report => sender() ! counts
      case "" =>
        log.info(s"[nullpointer] will be triggered")
        throw new NullPointerException("sentence is empty")
      case sentence: String =>
        if (sentence.length > 20) {
          log.info("[runtime]");
          throw new RuntimeException("to long sentence")
        }
        else if (sentence(0).isUpper) {
          log.info("[illegal]");
          throw new IllegalArgumentException("must start with lower case")
        }
        else log.info("[counting]");
        counts += sentence.split(" ").length
      case _ => log.info("[exception]"); throw new Exception("can only receive strings")
    }
  }
}