package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object VotingSystem extends App {

  object Citizen {
    sealed trait CitizenMsg

    case class Vote(name: String) extends CitizenMsg

    case object VoteStatusRequest extends CitizenMsg

    case class VoteStatusReply(candidate: Option[String]) extends CitizenMsg
  }

  class Citizen extends Actor {

    import Citizen._

    override def receive: Receive = needToVote

    def needToVote: Receive = {
      case Vote(name) => context.become(alreadyVoteReceive(name))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def alreadyVoteReceive(name: String): Receive = {
      case Vote(_) => println("[already voted]")
      case VoteStatusRequest => println(s"[vote status request] ${self.path.name} "); sender() ! VoteStatusReply(Some(name))
    }
  }

  object VoteAggregator {
    sealed trait VoteAggregatorMsg

    case class AggregateVotes(citizens: Set[ActorRef])

    case object PrintIt extends VoteAggregatorMsg
  }

  class VoteAggregator extends Actor {

    import VoteAggregator._
    import Citizen._

    override def receive: Receive = aggVotesReceive

    def aggVotesReceive: Receive = {
      case AggregateVotes(citizens) =>
        citizens
          .foreach(c => {
            println("[requesting vote]");
            c ! VoteStatusRequest
          })
        println(s"becoming printVotes")
        context.become(printVotesReceive(List(), citizens))
    }

    def printVotesReceive(names: List[String], citizen: Set[ActorRef]): Receive = {
      case VoteStatusReply(None) => sender() ! VoteStatusRequest
      case VoteStatusReply(Some(name)) =>
        println(s"[processing vote] ${sender().path.name}")
        println(s"[remaining vote to process] $citizen")
        val remainder = citizen - sender()
        val queue = name +: names
        if (remainder.isEmpty) {
          println(s"[printing names]");
          queue.groupBy((x: String) => x).foreach(t => println(s"${t._1} ${t._2.length}"))
        } else context.become(printVotesReceive(queue, remainder))

    }
  }


  import akka.actors.VotingSystem.Citizen.{Vote}
  import akka.actors.VotingSystem.VoteAggregator.{AggregateVotes, PrintIt}

  val guardian = ActorSystem("guardian")
  val luis = guardian.actorOf(Props[Citizen], "luis")
  val anna = guardian.actorOf(Props[Citizen], "anna")
  val seba = guardian.actorOf(Props[Citizen], "seba")
  val voteAggregator = guardian.actorOf(Props[VoteAggregator], "aggregator")
  val votes = Set(luis, anna, seba)

  luis ! Vote("jimmy")
  luis ! Vote("jimmy")
  anna ! Vote("jimmy")
  seba ! Vote("george")
  voteAggregator ! AggregateVotes(votes)


  Thread.sleep(1000)

  guardian.terminate()

}
