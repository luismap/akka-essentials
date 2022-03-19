package akka.testing

import akka.actor.{Actor, ActorLogging, ActorRef}



object WCMaster {

  sealed trait WCMasterMsg

  case class Initialize(numberOfWorkers: Int) extends WCMasterMsg

  case class WordCounterReply(id: Int, count: Int) extends WCMasterMsg

  case class WordCounterTask(id: Int, task: String) extends WCMasterMsg

  case class Task(line: String) extends WCMasterMsg
}

class WCMaster extends Actor with ActorLogging {

  import WCMaster._

  override def receive: Receive = {
    case Initialize(workers) =>
      (0 to workers).foreach(e =>
        //simulating some small delay
        if (e % 3 == 0) context.actorOf(WCExecutor.props(100), s"workder-$e")
        else context.actorOf(WCExecutor.props(0), s"worker-$e")
      )
      context.become(clusterInitialized(0, context.children.toArray, 0, Map()))

  }

  def clusterInitialized(selectedWorker: Int, workerQueue: Array[ActorRef], id: Int, taskId: Map[Int, ActorRef]): Receive = {
    case Task(line) =>
      workerQueue(selectedWorker) ! WordCounterTask(id, line) //assign the task to the worker
      val nextWorker = (selectedWorker + 1) % workerQueue.length
      val newTaskId = taskId + (id -> sender())
      val newId = id + 1
      context.become(clusterInitialized(nextWorker, workerQueue, newId, newTaskId))
    case WordCounterReply(id, count) =>
      log.info(s"[${self.path.name}] ${sender().path.name} counted $count ")
      taskId(id) ! count
      val newTaskId = taskId - id
      context.become(clusterInitialized(selectedWorker, workerQueue, id, newTaskId))


  }

}
