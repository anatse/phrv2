package controllers

import javax.inject.{Inject, Singleton}
import model.DrugsFindRq
import play.api.mvc.{AbstractController, ControllerComponents}
import service.DrugService

import scala.concurrent.ExecutionContext

@Singleton
class ProductController @Inject()(cc: ControllerComponents, drugsService: DrugService)(implicit ex: ExecutionContext) extends AbstractController(cc) {
  import model.ModelImplicits._

  def combinedSearchDrugsProducts = Action(parse.json[DrugsFindRq]).async { implicit request =>
    val find = request.body
    find.text match {
      case Some(text) => drugsService.combinedSearch(find.copy(pageSize = find.pageSize + 1)).map (
        rows => makeResult(rows, find.pageSize, find.offset)
      )

      case _ => drugsService.findAll(None, find.offset, find.pageSize).map (
        rows => makeResult(rows, find.pageSize, find.offset)
      )
    }
  }

  def initDB = Action.async {
    drugsService.createTextIndex().map(_ => Ok("OK"))
  }
}

