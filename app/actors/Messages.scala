package actors

import akka.actor.ActorRef

case class LoadDrug(drugName: String)
case class LoadFromSite(observer: Option[ActorRef])
case class ConnectDrug(drugName: String)
case class LoadedDrug (tradeName: String)