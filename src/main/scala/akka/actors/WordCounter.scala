package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.actors.WordCounter.Utils.counter

import java.io.File
import scala.collection.immutable.Queue
import scala.collection.mutable

object WordCounter extends App {

  object Utils {
    /**
     * given a path, return an array
     * of files
     *
     * @param path
     * @return
     */
    def fileReader(path: String) = {
      val files = new File(path).listFiles()
      files.filter(_.isFile)
    }


    /**
     * given a file, read its
     *
     * @param files
     * @return
     */
    def fileProcessor(file: File) = {
      for {
        line <- scala.io.Source.fromFile(file).getLines().toList
        if !line.matches(raw"\s*\)*")
      } yield {
        line
      }
    }

    /**
     * given a line, count how many words
     * (base on just white space)
     *
     * @param line
     * @return
     */
    def counter(line: String): Int = line.split(raw"\s+").length
  }

  object WorkerStatus extends Enumeration {
    type WorkerStatus = Value
    val WAITING, WORKING = Value
  }

  object WCMaster {
    sealed trait WCMasterMsg

    case class Initialize(numberOfWorkers: Int) extends WCMasterMsg

    case class WordCounterReply(id: Int, count: Int) extends WCMasterMsg

    case class WordCounterTask(id: Int, task: String) extends WCMasterMsg

    case class Task(line: String) extends WCMasterMsg
  }

  class WCMaster extends Actor {

    import WCMaster._
    import WorkerStatus._

    override def receive: Receive = {
      case Initialize(workers) =>
        (0 to workers).foreach(e =>
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
        println(s"[${self.path.name}] ${sender().path.name} counted $count ")
        taskId(id) ! count
        val newTaskId = taskId - id
        context.become(clusterInitialized(selectedWorker, workerQueue, id, newTaskId))


    }
  }

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


  class Client(file: List[String]) extends Actor {

    import WCMaster._

    override def receive: Receive = {
      case "start" =>
        val wcMaster = context.actorOf(Props[WCMaster], "wcMaster")
        wcMaster ! Initialize(2)
        file.foreach(
          line => wcMaster ! Task(line)
        )
        context.become(aggResponse(0))

    }

    def aggResponse(i: Int): Receive = {
      case count: Int =>
        val agg = i + count
        println(s"[${self.path.name} got $count] aggregating to $agg")
        context.become(aggResponse(agg))
      case "CurrentCount" =>
        println(s"[${self.path.name}] current count $i")

    }
  }


  import Utils._

  import WCMaster._

  val userGuardian = ActorSystem("CounterHandler")


  fileReader(".")
    .map(file => (file.getName, fileProcessor(file)))
    .foreach(
      fileContent => {
        val client = userGuardian.actorOf(Props(new Client(fileContent._2)), s"${fileContent._1}")
        client ! "start"
      }

    )

  val client1 = userGuardian.actorSelection("/user/build.sbt")

  Thread.sleep(1000)
  client1 ! "CurrentCount"
  userGuardian.terminate()
}
