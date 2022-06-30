package akka.patterns

import akka.actor.{ActorSystem, Props}
import akka.patterns.AskPattern.{AuthManager, PipedAuthManager}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite, WordSpecLike}

class AskPatternSpec
  extends TestKit(ActorSystem("askPatternGuardian"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)



  "An authManager" should {
   authenticateSuite(Props[AuthManager])
  }

  "A pipedAuthManager" should {
    authenticateSuite(Props[PipedAuthManager])
  }

  def authenticateSuite(props:  Props)  = {
    import AuthManager._
    "fail to authenticate a user is user not found" in {

      val authManager = system.actorOf(props)
      authManager ! Authenticate("luis", "pwd")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("luis", "pwd")
      authManager ! Authenticate("luis", "pd")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }
    "Succeed authenticate if valid user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("luis", "pwd")
      authManager ! Authenticate("luis", "pwd")
      expectMsg(AuthSuccess("luis"))
    }
  }


}
