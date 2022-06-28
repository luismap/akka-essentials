package akka.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.DurationInt

object MailBoxes extends App {


  val systemActor = ActorSystem("systemActor", ConfigFactory.load().getConfig("mailbox-demo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /**
   * A priority mail box
   * (we want to route messages base on som priority
   * ex. PO, P1,  ect
   *
   */

  //step1 mailbox definition
  class PriorityTickets(settings: ActorSystem.Settings, config: Config)
  extends UnboundedPriorityMailbox(
    PriorityGenerator(
      {
        case msg: String if msg.startsWith("[P0]") => 0
        case msg: String if msg.startsWith("[P1]") => 1
        case msg: String if msg.startsWith("[P2]") => 2
        case msg: String if msg.startsWith("[P3]") => 3
        case _ => 4
      }
    )
  )

  //step2 create config for the mail-box

  //step3 attach dispatcher

  val saWithCustomMailBox = systemActor.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
  implicit val executionContext = systemActor.dispatcher

  systemActor.scheduler.scheduleOnce(0 millis) {
    saWithCustomMailBox ! "asdfasdf"
    saWithCustomMailBox ! "[P3] hello"
    saWithCustomMailBox ! "[P2] nice"
    saWithCustomMailBox ! "[P1] world"
    saWithCustomMailBox ! "[P0] hello"
    saWithCustomMailBox ! "asdfasdf"

  }

  /**
   * control aware mailbox
   * - you want to process a message regardless of which message was before
    */

  //step 1 mark important messages and control messages
  case object ManagementTicket extends ControlMessage

  //step 2 configure who gets the mail box
  //attach the mailbox
  val controlAwareActor = systemActor.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))


  systemActor.scheduler.scheduleOnce(3 seconds) {
    controlAwareActor ! "[PO] the nw"
    controlAwareActor ! "[P2] the nw"
    controlAwareActor ! "[P1] the nw"
    controlAwareActor ! ManagementTicket

  }

  //step 3, using deployment configs for the actor name
  val altControlActor = systemActor.actorOf(Props[SimpleActor], "altControlActor")
  systemActor.scheduler.scheduleOnce(6 seconds) {
    altControlActor ! "[PO] the nw"
    altControlActor ! "[P2] the nw"
    altControlActor ! "[P1] the nw"
    altControlActor ! ManagementTicket

  }


  systemActor.scheduler.scheduleOnce(12 seconds)(
    systemActor.terminate()
  )(systemActor.dispatcher)
}
