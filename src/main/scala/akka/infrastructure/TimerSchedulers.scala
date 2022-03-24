package akka.infrastructure

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration.DurationInt

object TimerSchedulers extends App {

  class SchedulerActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case message => log.info(s"[${self.path.name}] ${message.toString}")
    }
  }

  val guardian = ActorSystem("guardian")
  val schedulerActor = guardian.actorOf(Props[SchedulerActor],"schedulerActor")

  implicit val contextDispatcher = guardian.dispatcher

  guardian.log.info("[scheduling] a task")
  guardian.scheduler.scheduleOnce(1 seconds){
    schedulerActor ! "hello"
  }
  //scheduler returns a cancellable
  val schedule: Cancellable = guardian.scheduler.schedule(1 seconds, 2 seconds){
    schedulerActor ! "calling every 2 seconds"
  }

  guardian.scheduler.scheduleOnce(10 seconds){
    schedule.cancel() //cancelling the schedule
  }

  class SelfCancellingActor extends Actor with ActorLogging {

    def timeOutWindow(): Cancellable = {
      context.system.scheduler.scheduleOnce(1 seconds) {
        self ! "timeout"
      }
    }

    override def receive: Receive = handlingTimeout(timeOutWindow())

    def handlingTimeout(timeout:Cancellable): Receive = {
      case "timeout" =>
        log.info(s"[${self.path.name}] is cancelling")
        context.stop(self)
      case message =>
        timeout.cancel()
        log.info(s"[${self.path.name}] receive ${message.toString}")
        context.become(handlingTimeout(timeOutWindow()))

    }
  }

  var selfCancellingActor: ActorRef = null
  guardian.scheduler.scheduleOnce(12 seconds) {
    selfCancellingActor = guardian.actorOf(Props[SelfCancellingActor], "selfCancellingActor")
  }

  val selfCancelScheduler = guardian.scheduler.schedule(13 seconds, 500 millis ){
    selfCancellingActor ! "i'm alive"
  }

  guardian.scheduler.scheduleOnce(20 seconds){
    selfCancelScheduler.cancel()
  }


  /**
   * Timer trait, useful when you want to sent periodic messages to self
   */

  object TimerActor {
    trait TimerActorMsg
    case object TimerKey extends TimerActorMsg
    case object Start extends TimerActorMsg
    case object Reminder extends TimerActorMsg
    case object Stop extends TimerActorMsg
  }
  class TimerActor extends Actor with ActorLogging with Timers {
    import TimerActor._

    timers.startSingleTimer(TimerKey, Start, 500 millis )

    override def receive: Receive = {
      case Start =>
        log.info(s"[${self.path.name}] started")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      case Reminder =>
        log.info(s"[${self.path.name}] getting periodic Reminder")
      case Stop =>
        log.info(s"[${self.path.name}] stopping timer actor")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerActor = guardian.actorOf(Props[TimerActor],"timerActor")

  guardian.scheduler.scheduleOnce(20 seconds){
    import TimerActor.Stop
    timerActor ! Stop
  }



  guardian.scheduler.scheduleOnce(30 seconds){
    guardian.terminate()
  }
}
