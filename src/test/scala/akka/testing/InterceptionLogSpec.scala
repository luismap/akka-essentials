package akka.testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class InterceptionLogSpec
 //event filter need access to logs, adding the config in loggersTest
  extends TestKit(ActorSystem("interceptionLogSpec", ConfigFactory.load().getConfig("loggersTest")))
  with WordSpecLike
  with ImplicitSender
  with BeforeAndAfterAll {
  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  /**
   * event filters
   */

  import InterceptionLogSpec._
  val item = "pants"
  val card = "1233-1321-13232"
  val faultyCard = "023-000-0020"

  "A checkout flow" should {
    "correctly log the dispatch of an order" in {
      //changing config to 5s timeout
      EventFilter.info(pattern = s".* $item with order id [0-9]+", occurrences = 1) intercept {
        val checkOutFlow = system.actorOf(Props[CheckOutActor])
        checkOutFlow ! CheckOut(item, card)
      }

    }

    "should fail when faulty card" in  {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkOutFlow = system.actorOf(Props[CheckOutActor])
        checkOutFlow ! CheckOut(item, faultyCard)
      }
    }
  }
}

object InterceptionLogSpec {
  case class CheckOut(item: String, card: String)
  case class CheckCard(str: String)
  case class PaymentAccepted(card: String)
  case class PaymentDenied(card: String)
  case class DispatchOrder(str: String)
  object OrderConfirmed

  class CheckOutActor extends Actor {
    val paymentManager = context.actorOf(Props[PaymentManager])
    val fullfillmentManager = context.actorOf(Props[FullfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case CheckOut(item, card) =>
        paymentManager ! CheckCard(card)
        context.become(awaitingPayment(item, card))

    }

    def awaitingPayment(item: String, card: String): Receive = {
      case PaymentAccepted(cardA) =>
        if (cardA == card) {
          fullfillmentManager ! DispatchOrder(item)
          context.become(awaitingFullfillment(item))
        }

      case PaymentDenied =>
        throw new RuntimeException("Denied payment")
    }

    def awaitingFullfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }
  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case CheckCard(card) =>
        if (card.startsWith("0")) sender() ! PaymentDenied
        else {
          //simulating a wait
          Thread.sleep(4000)
          sender() ! PaymentAccepted(card)
        }
    }
  }

  class FullfillmentManager extends Actor with ActorLogging {
    override def receive: Receive = fullfilling(0)

    def fullfilling(id: Int): Receive = {
      case DispatchOrder(item) =>
        log.info(s"[processed item] $item with order id $id")
        sender() ! OrderConfirmed
        context.become(fullfilling(id + 1))
    }
  }
}
