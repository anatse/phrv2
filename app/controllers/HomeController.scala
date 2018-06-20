package controllers

import actors.{ConnectDrug, LoadDrug, LoadFromSite}
import akka.actor.ActorRef
import javax.inject._
import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import service.DrugImportService

import scala.concurrent.{ExecutionContext, Future}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, @Named("load-rls-actor")loadRls: ActorRef, drugImportService: DrugImportService)(implicit ex: ExecutionContext) extends AbstractController(cc) {
  import model.ModelImplicits._
//  import scala.concurrent.ExecutionContext.Implicits.global

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
    loadRls ! LoadFromSite
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
}
