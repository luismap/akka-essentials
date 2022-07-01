package akka.patterns

import akka.actor.{ActorSystem, Props}
import akka.patterns.FSM.vendingMachine
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationDouble


class FSMTest extends TestKit(ActorSystem("FSM"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {
  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import FSM.VendingMachine._
  import FSM.VendingMachine

  def vendMachineRequest(product: String) = {
    val vendM = system.actorOf(Props[VendingMachine])
    vendM ! Initialize(Map("coke" -> 20, "fanta" -> 10), Map("coke" -> 2, "fanta" -> 1))
    vendM ! RequestProduct(product)
    vendM
  }

  "A vending machine" should {
    "error when not initialize" in {
      val vendingMachine = system.actorOf(Props[VendingMachine])
      vendingMachine ! RequestProduct("coke")
      expectMsg(VendingError(MACHINE_NO_INITIALIZE))
    }

    "report product not available" in {
      vendMachineRequest("sprite")
      expectMsg(VendingError(PRODUCT_NO_AVAILABLE))
    }

    "reply with instructions to pay" in {
      vendMachineRequest("coke")
      expectMsg(Instruction(s"to pay for coke = 2"))
      within(1.5 seconds) {
        expectMsg(VendingError(TRANSACTION_TIMEOUT))
      }
    }

    "timeout in case no payment made" in {
      vendMachineRequest("coke")
      expectMsg(Instruction("to pay for coke = 2"))

      within(1.5 seconds) {
        expectMsg(VendingError(TRANSACTION_TIMEOUT))
      }
    }

    "handle partial money delivery" in {
      val vendM = vendMachineRequest("coke")
      expectMsg(Instruction("to pay for coke = 2"))
      vendM ! ReceiveMoney(1)
      expectMsg(Instruction("left to pay coke = 1"))

      within(1.5 seconds){
        expectMsg(VendingError(TRANSACTION_TIMEOUT))
        expectMsg(GiveBackChange(1))
      }
    }

    "deliver product if all money inserted" in {
      val vendm = vendMachineRequest("coke")
      expectMsg(Instruction("to pay for coke = 2"))
      vendm ! ReceiveMoney(2)
      expectMsg(Deliver("coke"))
    }

    "give back change and request a new product" in {
      val vendm = vendMachineRequest("coke")
      expectMsg(Instruction("to pay for coke = 2"))
      vendm ! ReceiveMoney(1)
      expectMsg(Instruction("left to pay coke = 1"))
      vendm ! ReceiveMoney(4)
      expectMsg(GiveBackChange(3))
      expectMsg(Deliver("coke"))

      vendm ! RequestProduct("fanta")
      expectMsg(Instruction("to pay for fanta = 1"))

      within(1.5 seconds) {
        expectMsg(VendingError(TRANSACTION_TIMEOUT))
      }
    }

  }
}
