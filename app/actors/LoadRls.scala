package actors

import akka.actor.Actor
import akka.event.Logging
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import javax.inject.Inject
import play.api.Configuration
import service.DrugService
import utils.PhrLogger

class LoadRls @Inject() (configuration: Configuration, drugService: DrugService) extends Actor with PhrLogger {
  val log = Logging(context.system, this)
  val workerActorCount = configuration.get[Int]("rlsLoader.workActor.count")

  private final def makeLocalRouter = {
    val routes = Vector.fill(workerActorCount) {
      val worker = context.actorOf(RlsWorker.props)
      context watch worker
      ActorRefRoutee (worker)
    }

    Router(RoundRobinRoutingLogic(), routes)
  }

  override def receive: Receive = {
    case LoadDrug(drugName) =>
  }
}
