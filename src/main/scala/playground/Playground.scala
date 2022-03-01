package playground

import akka.actor.ActorSystem

object Playground extends App{

  val actor = ActorSystem("HelloAkka")

  println(actor.name)



}
