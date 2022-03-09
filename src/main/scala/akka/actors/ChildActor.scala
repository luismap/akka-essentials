package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.actors.ChildActor.Parent.{CreateChild, TellChild}

object ChildActor extends App {

  object Parent {
    sealed trait ParentMsg

    case class CreateChild(name: String) extends ParentMsg

    case class TellChild(msg: String) extends ParentMsg
  }

  class Parent extends Actor {
    override def receive: Receive = {
      case CreateChild(name) =>
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(msg) => childRef forward msg
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message: String => println(s"[child ${self.path.name}] got msg: $message")
    }
  }

  val guardian = ActorSystem("guardian")
  val parent = guardian.actorOf(Props[Parent], "parent")
  parent ! CreateChild("luis")
  parent ! TellChild("hello")


  /**
   * guardian actors
   * /system = related to sys staff
   * /user = for users
   * / = handles all of above
   */

  /**
   * actor selection
   * you can find an actor by its path
   */

  val childref = guardian.actorSelection("/user/parent/luis")
  childref ! "found you"

  Thread.sleep(1000)

  /**
   * IMPORTANT!!!
   *
   * NEVER PASS MUTABLE ACTOR STATE OR `THIS` REFERENCE TO CHILD ACTORS
   * (because you will have access to the instances methods and you bypass the message
   * handling of akka)
   * */


  object NaiveBankAcc {
    sealed trait NaiveBankAccMsg

    case class Withdraw(amount: Int) extends NaiveBankAccMsg

    case class Deposit(amount: Int) extends NaiveBankAccMsg

    case object InitializeAcc extends NaiveBankAccMsg
  }

  class NaiveBankAcc extends Actor {

    import NaiveBankAcc._
    import CreditCard.AttachToAccount

    var amount = 0

    override def receive: Receive = {
      case InitializeAcc =>
        val creditActor = context.actorOf(Props[CreditCard], "card")
        println(s"[attaching account] ${creditActor.path.name}")
        creditActor ! AttachToAccount(this) //no good
      case Withdraw(amount) => withdraw(amount)
      case Deposit(amount) => deposit(amount)
    }

    def withdraw(i: Int) = {
      println(s"${self.path} withdrawing $i from total $amount")
      amount -= i
    }

    def deposit(i: Int) = {
      println(s"${self.path} depositing $i from total $amount")
      amount += i
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAcc: NaiveBankAcc)
    case object CheckStatus
  }

  class CreditCard extends Actor {

    import CreditCard._

    override def receive: Receive = {
      case AttachToAccount(account) =>
        println(s"[becoming attacheToAccount] for actor ${self.path}")
        context.become(attachedToAccount(account))
    }

    def attachedToAccount(acc: NaiveBankAcc): Receive = {
      case CheckStatus =>
        println(s"[checking status] balance ${acc.amount}")
        println(s"wrong withdraw of money ;)")
        acc.withdraw(20) //here we break encapsulation, we have access to actor without
        //following the correct actor protocol NO NO GOOD
    }
  }


  import NaiveBankAcc._
  import CreditCard.CheckStatus

  val naiveBankAcc = guardian.actorOf(Props[NaiveBankAcc],"account")
  naiveBankAcc ! InitializeAcc
  naiveBankAcc ! Deposit(10)
  val card = guardian.actorSelection("/user/account/card")

  Thread.sleep(1000)
  card ! CheckStatus
  card ! CheckStatus


  guardian.terminate()
}
