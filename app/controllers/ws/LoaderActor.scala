package controllers.ws

import actors.{LoadFromSite, LoadedDrug}
import akka.actor.{Actor, ActorRef, Props}
import utils.PhrLogger

object LoaderActor {
  def props(out: ActorRef, loadRls: ActorRef) = Props(new LoaderActor(out, loadRls))
}

class LoaderActor(out: ActorRef, loadRls: ActorRef) extends Actor with PhrLogger {
  override def receive: Receive = {
    case msg: String =>
      out ! ("I received your message: " + msg)
      loadRls ! LoadFromSite(Some(self))

    case LoadedDrug(tradeName) => out ! s"Load drug with trade name: $tradeName"
  }

  override def postStop() = {
    logger.info("Socket closed")
  }
}
