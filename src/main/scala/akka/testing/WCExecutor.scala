package akka.testing

import akka.actor.{Actor, Props}

object WCExecutor {
  def props(delay: Int): Props =
    Props(new WCExecutor(delay))

}

case class WCExecutor(delay: Int = 0) extends Actor {

  import WCMaster._
  import Utils.counter

  override def receive: Receive = {
    case WordCounterTask(id, task) =>
      sender() ! WordCounterReply(id, counter(task))
  }
}