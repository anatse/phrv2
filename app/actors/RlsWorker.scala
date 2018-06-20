package actors

import akka.actor.{Actor, Props}
import utils.PhrLogger

class RlsWorker extends Actor with PhrLogger {
  override def receive: Receive = {
    case LoadDrug(drugName) =>
  }
}

object RlsWorker {
  def props = Props[RlsWorker]
}