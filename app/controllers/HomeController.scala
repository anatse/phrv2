package controllers

import actors.{ConnectDrug, LoadFromSite}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import controllers.ws.LoaderActor
import javax.inject._
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import service.DrugImportService

import scala.concurrent.{ExecutionContext, Future}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, @Named("load-rls-actor")loadRls: ActorRef, drugImportService: DrugImportService)
                              (implicit ex: ExecutionContext, system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  import model.ModelImplicits._

  var wsobserver:Option[ActorRef] = None

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def loadExcelFromRozminzdrav = Action.async {  implicit request =>
    loadRls ! LoadFromSite(wsobserver)
    Future.successful(Ok("loading started"))
  }

  def getAllDrugs = Action.async {  implicit request =>
    drugImportService.findAll.map (rows =>
      Ok(Json.obj("rows" -> rows))
    )
  }

  def findGroup(drugName: String) =  Action.async { implicit request =>
    loadRls ! ConnectDrug (drugName)
    Future.successful(Ok("Message sent"))
  }

  /**
    * Web socket handler. May be tested with https://www.websocket.org/echo.html
    * @return
    */
  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      LoaderActor.props(out, loadRls)
    }
  }
}
