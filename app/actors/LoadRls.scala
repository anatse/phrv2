package actors

import akka.actor.Actor
import akka.event.Logging

class LoadRls extends Actor {
  val log = Logging(context.system, this)

  override def receive: Receive = {
    case LoadDrug(drugName) =>
  }
}
