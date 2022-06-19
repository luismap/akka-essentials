package akka.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testing.TimedAssertionsSpec.Worker
import akka.testing.TimedAssertionsSpec.Worker.TaskDone
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt
import scala.util.Random

class TimedAssertionsSpec
  extends TestKit(ActorSystem("timeAssertions", ConfigFactory.load().getConfig("probeConfig.akka")))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {
  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "[time box test] worker" should {
    val worker = system.actorOf(Props[Worker])
    "reply within [500, 1000millis]" in {
      within(500 millis, 1 second) {
        worker ! "long work"
        expectMsg(TaskDone(45))
      }
    }

    "reply at a reasonable cadence [under 500 millis]" in {
      within(1 second) {
        worker ! "rapid work"
        val result: Seq[Int] = receiveWhile[Int](max = 1 second, idle = 500 millis, messages = 100) {
          case TaskDone(cnt) => cnt
        }

        assert(result.sum == 100)
      }
    }

    "test probe does not abide by the within timebox, must fail with timeout" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(worker, "long work")
        // will timeout, default config change to 0.3s
        intercept[AssertionError](
          probe.expectMsg(TaskDone(45))
        )
      }
    }
  }

}

object TimedAssertionsSpec {

  object Worker {
    trait WorkerMsg

    case class TaskDone(cnt: Int) extends WorkerMsg
  }

  class Worker extends Actor {

    import Worker._

    override def receive: Receive = {
      case "long work" =>
        //simulate long work
        Thread.sleep(500)
        sender() ! TaskDone(45)

      case "rapid work" =>
        val rnd = new Random()
        for (_ <- 0 to 100) {
          Thread.sleep(rnd.nextInt(5))
          sender() ! TaskDone(1)
        }
    }
  }
}
