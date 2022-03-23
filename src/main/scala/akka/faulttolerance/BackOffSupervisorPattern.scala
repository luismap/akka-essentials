package akka.faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.io.Source

object BackOffSupervisorPattern extends App {

  /**
   * because of supervision, if an actor access
   * external resources and it becomes unavailable for a while
   * a lot of exception will be triggerd, and the supervision
   * will be started. This can cause some headaches, that is why
   * BackOff pattern can be implemented
   */

  object FileActor {
    object ReadFile

    object ReadNonExistentFile
  }

  class FileActor extends Actor with ActorLogging {

    import FileActor._


    override def preStart(): Unit = log.warning(s"[${self.path.name}] is starting")

    override def postStop(): Unit = log.warning(s"[${self.path.name}] has stopped")


    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning(s"[restarting] ${self.path.name} is restarting")

    var datasource: Source = null

    override def receive: Receive = {
      case ReadFile =>
        if (datasource == null) {
          datasource = Source.fromFile(new File("src/main/resources/files/myfile.txt"))
          log.warning(s"[read file content] ${datasource.getLines().toList}")
        }
      case ReadNonExistentFile =>
        datasource = Source.fromFile(new File("src/main/resources/files/mynonfile.txt"))
        log.warning(s"[read file content] ${datasource.getLines().toList}")
      case "status" => log.info("i'm alive")
    }
  }

  val guardian = ActorSystem("guardian")
  val fileActor = guardian.actorOf(Props[FileActor], "fileactor")

  import FileActor._

  fileActor ! ReadFile
  fileActor ! ReadNonExistentFile
  Thread.sleep(200)
  fileActor ! "status"

  /**
   * a backoff supervisor
   * - create a parent actor of type backoff
   * - creates a child of type props
   * - defines new policies for on failure
   * - increase delay with some random factor
   */
  val fileReadBackoffSupervisor = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileActor],
      "backoffFileActor",
      3 seconds,
      30 seconds,
      0.2
    )
  )

  val fileReadBackoff = guardian.actorOf(fileReadBackoffSupervisor, "fileReadBackoffSupervisor")

  fileReadBackoff ! ReadNonExistentFile

  fileReadBackoff ! "status"

  Thread.sleep(4000)

  class StopActor extends FileActor {
    override def preStart(): Unit = {
      /**
       * because file do not exists, the actor will stop
       * we can tweak this behaviour by wrapping in a backoff supervisor
       */
      datasource = Source.fromFile(new File("src/main/resources/files/mynonfile.txt"))
      log.warning(s"[read file content] ${datasource.getLines().toList}")
    }
  }

  val stopFileBackoffSupervisor = BackoffSupervisor.props(
    Backoff.onStop(
      Props[StopActor],
      "stopFileActor",
      3 seconds,
      30 seconds,
      0.1
    )
  )

  val stopFileSupervisor = guardian.actorOf(stopFileBackoffSupervisor,"stopFileBackoffSupervisorAc")



  guardian.terminate()
}
