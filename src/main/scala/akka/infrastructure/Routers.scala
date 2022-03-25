package akka.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Kill, PoisonPill, Props, Terminated}
import akka.routing.{ActorRefRoutee, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

object Routers extends App {
  /**
   * Routers
   * a way of sending messages to all child
   */

  /**
   * manual router
   */
  class ManualRouterMaster extends Actor {
    private val workers = for (w <- 0 until 5) yield {
      val worker = context.actorOf(Props[Worker], s"worker$w")
      context.watch(worker)
      ActorRefRoutee(worker)
    }
    //roundrobing logic, for the router (you can choose other logics)
    //the router, will choose to which child to send the message
    private val router = Router(RoundRobinRoutingLogic(),workers)

    override def receive: Receive = handlingMessages(router: Router,5)

    def handlingMessages(router: Router, cid: Int): Receive = {
      case Terminated(ref) =>
        val tmpRouter = router.removeRoutee(ref)
        val newWorker = context.actorOf(Props[Worker], s"worker$cid")
        context.watch(newWorker)
        val routee = ActorRefRoutee(newWorker)
        context.become(handlingMessages(tmpRouter.addRoutee(routee),cid + 1))
      case "kill a worker" =>
        context.children.head ! Kill
      case message =>
        //for any messsage, route it, with the sender
        router.route(message, sender())
    }
  }

  class Worker extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"[${self.path.name}] got message: ${message.toString}")
    }
  }

  val systemGuardian = ActorSystem("routerGuardian", ConfigFactory.load().getConfig("routersDemo"))
  val masterActor = systemGuardian.actorOf(Props[ManualRouterMaster], "routerMaster")

  implicit val executionContext = systemGuardian.dispatcher

  systemGuardian.scheduler.schedule(0 millis, 100 millis) {
    masterActor ! "route this message"
  }

  systemGuardian.scheduler.schedule(1 seconds, 500 millis){
    masterActor ! "kill a worker"
  }

  systemGuardian.scheduler.scheduleOnce(6 seconds){
    masterActor ! PoisonPill
  }

  /**
   * another way of routing
   *  A router with its own children pool
   */

  val poolRouting = systemGuardian.actorOf(RoundRobinPool(5).props(Props[Worker]))

  systemGuardian.scheduler.schedule(6 seconds,500 millis){
    poolRouting ! "pool route this message"
  }

  systemGuardian.scheduler.scheduleOnce(12 seconds){
    poolRouting ! PoisonPill
  }

  /**
   * A router using default configs
   */

  val defaultRouterPool = systemGuardian.actorOf(FromConfig.props(Props[Worker]),"poolMasterName")

  systemGuardian.scheduler.schedule(12 seconds,500 millis){
    defaultRouterPool ! "pool route this message"
  }
  systemGuardian.scheduler.scheduleOnce(16 seconds){
    systemGuardian.terminate()
  }

  /**
   * A router with actor created elsewhere
   */
  val workerList = (0 to 5).map(w => systemGuardian.actorOf(Props[Worker], s"groupW$w")).toList
  val workerPath = workerList.map(_.path.toString)
  val groupMaster = systemGuardian.actorOf(RoundRobinGroup(workerPath).props())

  /**
   * same as above but using config file
   */

}
