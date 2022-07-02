package akka.patterns

import akka.actor.{ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.patterns.VendingMachine.VendingMachine.{Initialize, RequestProduct}
import akka.patterns.VendingMachine.{ClientWithSimpleHandling, VendingMachine, client}

import scala.concurrent.duration.DurationInt

object FSMVendingMachine extends App {

  /**
   * to create FSM we need to follow the following steps
   */

  //1- create the states and data of the actors
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitingForTransaction extends VendingState

  trait VendingData
  case object UninitializedData extends VendingData
  case class InitializedData(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData
  case class WaitingForTransactionData(inventory: Map[String, Int],
                                       prices: Map[String, Int],
                                       item: String,
                                       money: Int,
                                       requester: ActorRef
                                      ) extends VendingData

 class FSMVendingMach extends FSM[VendingState, VendingData] {
   //reusing messages from vending machine, to not recreate those now
   import VendingMachine.VendingMachine._


   //initial state
   //instead of a receive handler we need to handle Event(message, data) events
   startWith(Idle, UninitializedData)
   //
   when(Idle){
     case Event(Initialize(inventory, prices), UninitializedData ) =>
       goto(Operational) using InitializedData(inventory, prices)
     case _ =>
       sender() ! VendingError(MACHINE_NO_INITIALIZE)
       stay()
   }
   when(Operational){
     case Event(RequestProduct(item), InitializedData(inventory,prices)) =>
     inventory.get(item) match {
       case None | Some(0) =>
         sender() ! VendingError(PRODUCT_NO_AVAILABLE)
         stay()
       case Some(_) =>
         val price = prices(item)
         sender() ! Instruction(s"to pay for $item = $price")
         goto(WaitingForTransaction) using WaitingForTransactionData(
           inventory, prices, item, 0, sender()
         )
     }
   }
   when(WaitingForTransaction, stateTimeout = 1 seconds){
     case Event(StateTimeout,WaitingForTransactionData(inventory, prices, _, money, requester) ) =>
       requester ! VendingError(TRANSACTION_TIMEOUT)
       if (money > 0) requester ! GiveBackChange(money)
       goto(Operational) using InitializedData(inventory, prices)
     case Event(ReceiveMoney(amount), WaitingForTransactionData(inventory, prices, item, money, requester)) =>
       val price = prices(item)
       val currentBalance = money + amount
       val totalToPay = price - currentBalance
       if (totalToPay <= 0) {
         if (totalToPay < 0) requester ! GiveBackChange(math.abs(totalToPay))
         val newQty = inventory(item) - 1
         val newInv = inventory + (item -> newQty)
         requester ! Deliver(item)
         log.info(s"[new inventory] $newInv")
         goto(Operational) using (InitializedData(newInv, prices))
       } else {
         requester ! Instruction(s"left to pay $item = ${math.abs(totalToPay)}")
         stay() using WaitingForTransactionData(
           inventory, prices, item, currentBalance, requester
         )
       }
   }

   whenUnhandled {
     case Event(_,_) =>
       sender() ! VendingError("command not found")
       stay()
   }

   onTransition {
     case stateA -> stateB => log.info(s"[transitioning] from state $stateA to $stateB")
   }

   //initialize the FSM
   initialize()
 }

  val actorSystem = ActorSystem("vendingMguardian")
  val vendingMachine = actorSystem.actorOf(Props[FSMVendingMach], "ven-mach")
  val client = actorSystem.actorOf(Props[client], "luis")
  //val simpleClient = actorSystem.actorOf(Props[ClientWithSimpleHandling])
  val scheduler = actorSystem.scheduler

  implicit val sender = client
  implicit val execCtx = actorSystem.dispatcher


  vendingMachine ! Initialize(Map("coke" -> 20, "fanta" -> 10), Map("coke" -> 2, "fanta" -> 1))

  //vendingMachine.tell(RequestProduct("coke"), simpleClient)


  scheduler.schedule(0 millis, 1 seconds) {
    vendingMachine ! RequestProduct("coke")
  }

  scheduler.scheduleOnce(6.seconds) {
    actorSystem.terminate()
  }

}
