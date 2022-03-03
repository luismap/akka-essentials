package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.math.random
import scala.util.{Random, Try}

object Bank extends App {

  /**
   * create a bank
   *  - deposit
   *  - withdraw
   *  - statement
   *  reply
   *  - success
   *  - failure
   *
   *  actor user
   *  - interacts with banks
    */

  sealed trait BankMessage
  case class Deposit(qty: Int, person: ActorRef) extends BankMessage
  case class Withdraw(qty: Int, person: ActorRef) extends BankMessage
  case class Statement(person: ActorRef) extends BankMessage

  sealed trait Response
  case class Success(payload: String) extends Response
  case class Failure(code: String) extends Response

  case class Account(var amount: Int = 0) extends Actor {
   require(amount >= 0)

    override def receive: Receive = {
      case msg @ Deposit(_,_) => handleMsg(msg)
      case msg @ Withdraw(_,_) => handleMsg(msg)
      case msg @ Statement(_) => handleMsg(msg)
    }

    def handleMsg(bankMessage: BankMessage) = bankMessage match {
      case Deposit(qty,person) =>
        amount += qty
        println(s"[deposit] $qty" + "$")
        person ! Success(s"added $qty" + "$")
      case Withdraw(qty, person) =>
        if (amount - qty < 0) person ! Failure("no saldo available")
        else {
          println(s"[withdraw] withdrew $qty")
          amount -= qty
          person ! Success(s"withdrew $qty")
        }
      case Statement(person) =>
        println(s"[statement] your balance $amount" + "$")
        person ! Success

    }
  }

  case class Person(name: String) extends Actor {
    override def receive: Receive = {
      case msg @ Success(_) => handleMsg(msg)
      case msg @ Failure(_) => handleMsg(msg)
    }

    def handleMsg(response: Response) = response match {
      case Failure(code) => println(s"[failure] error >> $code")
      case Success(payload) => println(s"[success] $payload")
    }

  }

  val guardian = ActorSystem("guardian")
  val account = guardian.actorOf(Props(new Account(10)))
  val luis = guardian.actorOf(Props(new Person("luis")))
  val rnd = Random
  var check = rnd.nextInt(3)

  for (_ <- 0 to 20 ) {
    Thread.sleep(100)
    if ( check == 0) account ! Withdraw(6, luis)
    else if ( check == 1 ) account ! Deposit(2, luis)
    else account ! Statement
    check = rnd.nextInt(3)
  }

  guardian.terminate()
}
