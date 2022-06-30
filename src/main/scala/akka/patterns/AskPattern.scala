package akka.patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
//step 1 import the pattern
import akka.pattern.ask

object AskPattern extends App {

  /**
   * showing the ask patter using example of User authentication
   * to use it, you need to follow the steps listed throughout the file
   */

  /**
   * some key-value actor assuming the role of
   */
  object KVActor {
    trait KVActorMsg

    case class Read(key: String) extends KVActorMsg

    case class Write(key: String, value: String) extends KVActorMsg
  }

  class KVActor extends Actor with ActorLogging {

    import KVActor._

    override def receive: Receive = online(Map.empty)

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"[reading] for $key")
        sender() ! kv.get(key)
      case Write(key, value) =>
        log.info(s"[writing] key $key value $value")
        context.become(online(kv + (key -> value)))
    }
  }

  /**
   * user authenticator
   */
  object AuthManager {
    trait AuthManagerMsg

    case class RegisterUser(name: String, pwd: String) extends AuthManagerMsg

    case class Authenticate(name: String, pwd: String) extends AuthManagerMsg

    case class AuthFailure(message: String) extends AuthManagerMsg

    case class AuthSuccess(name: String) extends AuthManagerMsg

    val AUTH_FAILURE_NOT_FOUND = "could not find user"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "incorrect password"
    val AUTH_FAILURE_SYSTEM = "system error"
  }

  class AuthManager extends Actor with ActorLogging {

    import AuthManager._
    import KVActor._

    protected val authDb: ActorRef = context.actorOf(Props[KVActor])

    //step 2 logistics
    implicit val timeout = Timeout(1 seconds)
    implicit val execCtx = context.dispatcher

    override def receive: Receive = {
      case RegisterUser(name, pwd) =>
        log.info(s"[registering] user: $name")
        authDb ! Write(name, pwd)
      case Authenticate(name, pwd) => handleAuth(name, pwd)
      case AuthFailure(msg) => log.info(msg)
      case AuthSuccess(name) => log.info(s"[authsuccess] for user $name")


    }

    def handleAuth(name: String, pwd: String) = {
      val originalSender = sender()
      //step 3 ask the actor
      val future = authDb ? Read(name) //the ask returns a future
      //step 4 handle the future
      //step 4 get the original sender
      future.onComplete {
        //NEVER CALL METHOD OF ACTOR INSTANCE INSIDE OR ACCESS MUTABLE STATE INSIDE onComlplete
        //it breaks actor encapsulation
        case Success(None) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbpwd)) =>
          if (pwd == dbpwd) {
            log.info(s"[authSuccess] for user:$name")
            originalSender ! AuthSuccess(name)
          }
          else {
            originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
          }
        case Failure(_) => originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)

      }
    }
  }

  /**
   * a better way to do the ask pattern by using pipe
   */

  class PipedAuthManager extends AuthManager {
    import KVActor._
    import AuthManager._

    override def handleAuth(name: String, pwd: String): Unit = {
      val future = authDb ? Read(name)
      val responseRead = future.mapTo[Option[String]]
      val responseFuture = future.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbpwd) =>
          if (pwd == dbpwd) {
            log.info(s"[authSuccess] for user:$name")
            AuthSuccess(name)
          }
          else {
            AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
          }
        case _ => AuthFailure(AUTH_FAILURE_SYSTEM)
      }

      responseFuture.pipeTo(sender())
    }
  }

  /**
   * a simple client to handle the authentication
   * needs to handle the auth response
   */

  class Client extends Actor with ActorLogging {
    import AuthManager._
    override def receive: Receive = {
      case AuthFailure(message) => log.info(s"[authfailure] $message")
      case AuthSuccess(name) => log.info(s"[authsuccess] user: $name got auth")
    }
  }

  val systemActor = ActorSystem("guardian")
  val authManager = systemActor.actorOf(Props[AuthManager], "authManager")
  val pipedAuthManager = systemActor.actorOf(Props[PipedAuthManager], "pipedAuthManager")
  val client = systemActor.actorOf(Props[Client])

  implicit val exeCtx = systemActor.dispatcher
  implicit val sender: ActorRef = client
  import AuthManager._


  val scheduler = systemActor.scheduler


  scheduler.scheduleOnce(0 seconds) {
    authManager ! RegisterUser("luis", "pwd")
    authManager ! RegisterUser("Anna", "pwd")
  }

  scheduler.scheduleOnce(3 seconds) {
    authManager ! Authenticate("luis", "hello")
    authManager ! Authenticate("luis", "pwd")
    authManager ! Authenticate("hue", "pwd")
  }

  scheduler.scheduleOnce(4 seconds) {
    pipedAuthManager ! RegisterUser("luis", "pwd")
    pipedAuthManager ! RegisterUser("Anna", "pwd")
  }

  scheduler.scheduleOnce(6 seconds) {
    pipedAuthManager ! Authenticate("luis", "hello")
    pipedAuthManager ! Authenticate("luis", "pwd")
    pipedAuthManager ! Authenticate("hue", "pwd")
  }


  scheduler.scheduleOnce(12 seconds) {
    systemActor.terminate()
  }
}
