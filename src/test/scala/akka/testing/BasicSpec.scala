package akka.testing

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender //pass the TestActor as sender
  with WordSpecLike
  with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }


  "A simple actor" should {
    "send back the message" in {
      val simpleActor = system.actorOf(Props[BasicActor], "basicActor")
      val msg = "hello world"
      simpleActor ! msg
      expectMsg(msg) //time out comes from config akka.test.single-expect-default
    }
  }

  "NoResponse actor" should {
    "no reply after receiving a message" in {
      val noReplyActor = system.actorOf(Props[NoReplyActor], "noreply")
      val msg = "hello"
      noReplyActor ! msg

      expectNoMessage( 2 second)
    }
  }

  "lab actor" should {
    val labActor = system.actorOf(Props[LabActor])
    "reply with upper case message given a string" in {
      labActor ! "hello"
      val reply = expectMsgType[String]

      assert(reply == "HELLO")
    }
    "given a <greeting> message reply with either (hi, hello)" in {
      labActor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "given a `tech stack` message reply with (scala, python)" in {
      labActor ! "tech stack"
      expectMsgAllOf("scala", "python")
    }

    "given a `tech stack` message reply with 2 message" in {
      labActor ! "tech stack"
      val message: Seq[Any] = receiveN(2)

    }

    "given a `tech stack` message reply with 2 message for PF" in {
      labActor ! "tech stack"

      //handle message in different ways
      expectMsgPF() {
        case "scala" => //handle scala
        case "python" => //handle python
      }
    }

  }
}
