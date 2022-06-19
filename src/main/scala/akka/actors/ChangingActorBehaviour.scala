package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehaviour extends App {


  sealed trait FuzzyKidMsg
  case object KidAccept extends FuzzyKidMsg
  case object KidReject extends FuzzyKidMsg
  object Sentiment extends Enumeration {
    type Sentiment = Value
    val HAPPY, SAD = Value
  }

  /**
   * when working with actors, think about domains
   * the actor
   * the companion class(will be the domain def)
   */
  object FuzzyKid
  class FuzzyKid extends Actor {
    import Sentiment._
    import Mom._
    import Mom.Food._
    var state = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => println("[stateful got vegetable]"); state = SAD
      case Food(CHOCOLATE) => println("[stateful got chocolate]"); state = HAPPY
      case Ask(_) =>
        if (state== HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  /**
   * because mutation is not good,
   * stateless actor can handle mutation by stacking calls
   */
  object StatelessFuzzyKid
  class StatelessFuzzyKid extends Actor {
    import Mom._
    import Mom.Food._

    override def receive: Receive = happyReceive //entry state

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive)// change receiver to sad
      case Food(CHOCOLATE) => println("[statelesskid got chocolate]")//do nothing, is here
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => println("[statelesskid got veg]") //do nothing, is here
      case Food(CHOCOLATE) => context.become(happyReceive) //change receiver to happy
      case Ask(_) => sender() ! KidReject
    }

  }

  /**
   * stacking an unstacking message handler
   * .become(handler, false) will stack => stack.push(handler)
   * .unbecome(handler) will pop => stack.pop(handler)
   * ex.
   * emulating being sad if vegetables, less sad if chocolate
   */
  object StatelessFuzzyKidStack
  class StatelessFuzzyKidStack extends Actor {
    import Mom._
    import Mom.Food._

    override def receive: Receive = happyReceive //entry state

    def happyReceive: Receive = {
      case Food(VEGETABLE) => println(s"[${self.path.name} got veg]"); context.become(sadReceive, false)// change receiver to sad
      case Food(CHOCOLATE) => //do nothing, is here
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => println("[stacking sad]"); context.become(sadReceive, false)
      case Food(CHOCOLATE) => println("[unstacking sad]"); context.unbecome() //removing this handler from the stack
      case Ask(_) => sender() ! KidReject
    }

  }


  object Mom {
    object Food extends Enumeration {
      type Food = Value
      val VEGETABLE, CHOCOLATE = Value
    }

    sealed trait MomMsg
    case class Food(food: Food.Food) extends MomMsg
    case class Ask(msg: String) extends MomMsg
    case class MomInit[T <: MomMsg](actorRef: ActorRef, kidMsg: T) extends MomMsg
  }

  class Mom extends Actor {
    import Mom._

    override def receive: Receive = {
      case MomInit(kid, msg) =>
        kid ! msg
      case KidAccept => println(s"[kidaccept ${sender().path.name}] kid is happy")
      case KidReject => println(s"[kidreject ${sender().path.name}] kid is unhappy")
    }
  }

  import Mom._
  import Mom.Food._
  val guardian  = ActorSystem("guardian")
  val mom = guardian.actorOf(Props[Mom])
  val kid = guardian.actorOf(Props[FuzzyKid],"kid")
  val kidStateless = guardian.actorOf(Props[StatelessFuzzyKid], "stalessKid")
  val kidStatelessStack = guardian.actorOf(Props[StatelessFuzzyKidStack], "stalessKidStack")
  val vegetable = Food(VEGETABLE)
  val chocolate = Food(CHOCOLATE)

  mom ! MomInit(kid,vegetable)
  mom ! MomInit(kid,Ask("are you happy"))
  mom ! MomInit(kidStateless, chocolate)
  mom ! MomInit(kidStateless, Ask("are you happy"))
  mom ! MomInit(kidStatelessStack,vegetable)
  mom ! MomInit(kidStatelessStack, Ask("are you happy"))
  mom ! MomInit(kidStatelessStack,vegetable)
  mom ! MomInit(kidStatelessStack, Ask("are you happy"))
  mom ! MomInit(kidStatelessStack,chocolate)
  mom ! MomInit(kidStatelessStack, Ask("are you happy"))
  mom ! MomInit(kidStatelessStack,chocolate)
  mom ! MomInit(kidStatelessStack, Ask("are you happy"))
  mom ! MomInit(kidStatelessStack,chocolate)
  mom ! MomInit(kidStatelessStack, Ask("are you happy"))

  Thread.sleep(2000)
  guardian.terminate()
}
