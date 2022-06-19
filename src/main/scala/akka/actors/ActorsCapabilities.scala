package akka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsCapabilities  extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => context.sender() ! s"Hello ${context.sender().path.name}, is ${context.self.path.name}"
      case msg: String => println(s"[${context.self.path}] received message: $msg")
      case number: Int => println(s"[${self}] received number: $number")
      case smsg: SpecialMsg => println(s"[simple actor] special message handler: ${smsg.msg}")
      case SayHiTo(ref) => ref ! "Hi!" //by implicits it adds self as sender
      case ForwardingMessage(content, ref) => ref forward content
    }
  }

  val actorSystem = ActorSystem("topLevelActor")

  val simpleActor = actorSystem.actorOf(Props[SimpleActor], "simpleActor")
  simpleActor ! "asdf"
  simpleActor ! 45

  case class SpecialMsg(msg: String)

  simpleActor ! SpecialMsg("special message")

  /**
   * about actors,
   * - can be of any type
   * - must be immutable
   * - must be serializable
   * - have information about their context and themself (context.self === <this> in OOP)
   */


  val bob = actorSystem.actorOf(Props[SimpleActor], "bob")
  val alice = actorSystem.actorOf(Props[SimpleActor], "alice")

  case class SayHiTo(ref: ActorRef)

  //actors can reply to messages
  alice ! SayHiTo(bob)

  //deadLetters actor(when you send a message to the default implicit Actors.noSender
  alice ! "Hi!"

  //forwarding message
  case class ForwardingMessage(content: String, ref: ActorRef)

  alice ! ForwardingMessage("forwarding hello from alice Hello!", bob)

  actorSystem.terminate()
}
