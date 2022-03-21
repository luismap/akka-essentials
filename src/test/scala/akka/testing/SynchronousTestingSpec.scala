package akka.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

class SynchronousTestingSpec extends WordSpecLike with BeforeAndAfterAll {
  implicit val system = ActorSystem("system")
  import SynchronousTestingSpec._

  override protected def afterAll(): Unit = system.terminate()

  "a counter actor ref" should {
    "syncrhonously increase its value" in {
      //useful for synchronously tests, i.e no wait for timeout
      //communication happens in the calling thread
      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Increment
      assert(counter.underlyingActor.count == 1)
    }

    "increase counter at the call of the receive method" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Increment)
      assert(counter.underlyingActor.count == 1)
    }

    "work on the calling thread dispatcher" in {
      //making the counter actor to work in the same thread
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()
      probe.send(counter, Read)
      //asserting that the probe already received the message by implicit zero duration
      probe.expectMsg(Duration.Zero,0)
    }

  }
}

object SynchronousTestingSpec {
  trait CounterMsg

  object Increment extends CounterMsg

  object Decrement extends CounterMsg
  object Read extends CounterMsg

  class Counter extends Actor {
    var count = 0
    override def receive: Receive = counter(0)

    def counter(cnt: Int): Receive = {
      case Increment => count += 1; context.become(counter(cnt + 1))
      case Decrement => count -= 1; context.become(counter(cnt - 1))
      case Read => sender() ! cnt
    }
  }
}
