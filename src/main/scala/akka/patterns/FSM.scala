package akka.patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.patterns.AskPattern.Client
import akka.patterns.FSM.VendingMachine.{Instruction, ReceiveMoney}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object FSM extends App {

  /**
   * FSM(finite state machines)
   * use case of a Vending machine
   */

  object VendingMachine {
    sealed trait VendingMachineMsg

    case class Initialize(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingMachineMsg

    case class RequestProduct(product: String) extends VendingMachineMsg

    case class Instruction(instruction: String) extends VendingMachineMsg

    case class ReceiveMoney(amount: Int) extends VendingMachineMsg

    case class Deliver(product: String) extends VendingMachineMsg

    case class GiveBackChange(amount: Int) extends VendingMachineMsg

    case class VendingError(error: String) extends VendingMachineMsg

    case object ReceiveMoneyTimeout

    val MACHINE_NO_INITIALIZE = "machine no initialized"
    val PRODUCT_NO_AVAILABLE = "product not available"
    val TRANSACTION_TIMEOUT = "transaction timeout"
  }

  class VendingMachine extends Actor with ActorLogging {

    import VendingMachine._

    implicit val executionCtx = context.dispatcher

    override def receive: Receive = idle

    def idle: Receive = {
      case Initialize(inv, prices) =>
        log.info(s"[initializing] with inventory $inv")
        context.become(operational(inv, prices))
      case _ => sender() ! VendingError(MACHINE_NO_INITIALIZE)
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(item) => inventory.get(item) match {
        case None | Some(0) => sender() ! VendingError(PRODUCT_NO_AVAILABLE)
        case Some(_) =>
          val price = prices(item)
          sender() ! Instruction(s"to pay for $item = $price")
          context.become(waitingForTransaction(
            inventory, prices, item, 0, startReceivedMoneyTimeoutScheduler, sender()
          ))

      }
    }

    def startReceivedMoneyTimeoutScheduler =
      context.system.scheduler.scheduleOnce(1 second) {
        self ! ReceiveMoneyTimeout
      }

    def waitingForTransaction(
                               inventory: Map[String, Int],
                               prices: Map[String, Int],
                               item: String,
                               money: Int,
                               moneyTimeoutSchedule: Cancellable,
                               requester: ActorRef
                             ): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError(TRANSACTION_TIMEOUT)
        if (money > 0) requester ! GiveBackChange(money)
        context.become(operational(inventory, prices))

      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(item)
        val totalToPay = price - (amount + money)
        if (totalToPay <= 0) {
          if (totalToPay < 0) requester ! GiveBackChange(math.abs(totalToPay))
          val newQty = inventory(item) - 1
          val newInv = inventory + (item -> newQty)
          requester ! Deliver(item)
          log.info(s"[new inventory] $newInv")
          context.become(operational(newInv, prices))
        } else {
          requester ! Instruction(s"left to pay $item = ${math.abs(totalToPay)}")
          context.become(waitingForTransaction(
            inventory, prices, item, amount, startReceivedMoneyTimeoutScheduler, requester
          ))
        }

    }
  }

  /**
   * actor that simulate a payment
   */
  class client extends Actor with ActorLogging {
    private var bankaccount: Int = 30
    private val rnd = Random

    import VendingMachine._

    override def receive: Receive = {
      case VendingError(msg) => log.info(msg)
      case Deliver(item) => log.info(s"[delivered] a $item")
      case Instruction(msg) =>
        log.info(msg)
        val priceExtractor = raw".*(?<=\w+)\s*=\s*(\d+)" r
        val toPay = msg match {
          case priceExtractor(price) => price.toInt
        }
        if (rnd.nextInt(2) == 1) {
          val balance = bankaccount - toPay
          sender() ! ReceiveMoney(toPay)
          bankaccount = balance
          log.info(s"[balance] $bankaccount")
        } else {
          val newPay = toPay - 1 //simulate some payment differences
          val balance = bankaccount - newPay
          sender() ! ReceiveMoney(newPay)
          bankaccount = balance
          log.info(s"[balance] $bankaccount")
        }

      case GiveBackChange(change) =>
        bankaccount += change
        log.info(s"[change gotten] got change $change")
        log.info(s"[balance] $bankaccount")
    }
  }

  class ClientWithSimpleHandling extends client {
    override def receive: Receive = {
      case Instruction(inst) =>
        log.info(inst)
        sender() ! ReceiveMoney(1)
        context.become(standby)
    }

    def standby: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  val actorSystem = ActorSystem("vendingMguardian")
  val vendingMachine = actorSystem.actorOf(Props[VendingMachine], "ven-mach")
  val client = actorSystem.actorOf(Props[client], "luis")
  val simpleClient = actorSystem.actorOf(Props[ClientWithSimpleHandling])
  val scheduler = actorSystem.scheduler

  implicit val sender = client
  implicit val execCtx = actorSystem.dispatcher

  import VendingMachine._

  vendingMachine ! Initialize(Map("coke" -> 20, "fanta" -> 10), Map("coke" -> 2, "fanta" -> 1))

  vendingMachine.tell(RequestProduct("coke"), simpleClient)


  scheduler.schedule(0 millis, 1 seconds) {
    vendingMachine ! RequestProduct("coke")
  }

  scheduler.scheduleOnce(6.seconds) {
    actorSystem.terminate()
  }

}
