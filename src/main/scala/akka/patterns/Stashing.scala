package akka.patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

import scala.concurrent.duration.DurationInt

object Stashing extends App {
  /**
   * ex.
   * An actor that will process messages when open
   * or stash message when close
   */

  object ResourceActor {
    sealed trait ResourceActorMsg

    case object Open extends ResourceActorMsg

    case object Close extends ResourceActorMsg

    case object Read extends ResourceActorMsg

    case class Write(data: String) extends ResourceActorMsg
  }

  class ResourceActor extends Actor with ActorLogging with Stash {
    private var data: String = ""

    import ResourceActor._

    override def receive: Receive = close

    def close: Receive = {
      case Open =>
        log.info("[opening] resource")
        unstashAll() //adding all message to mailbox
        context.become(open)
      case message =>
        log.info(s"[stashing] $message")
        stash()
    }

    def open: Receive = {
      case Read =>
        log.info(s"[reading] $data")
      case Write(content) =>
        log.info(s"[writing] $content")
        data = content
      case Close =>
        log.info(s"[closing] resource")
        unstashAll()
        context.become(close)
      case message =>
        log.info(s"[stashing] $message")
        stash()
    }
  }

  val systemActor = ActorSystem()
  val resource = systemActor.actorOf(Props[ResourceActor])

  import ResourceActor._
  resource ! Write("hello")
  resource ! Read
  resource ! Open



  systemActor.scheduler.scheduleOnce(12 seconds)(
    systemActor.terminate()
  )(systemActor.dispatcher)
}
