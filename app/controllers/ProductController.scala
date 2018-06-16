package controllers

import java.nio.file.{Files, Paths, StandardCopyOption}

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import model._
import model.security.JWTEnv
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import service.DrugService
import service.security.{PhrIdentityService, WithRoles}
import utils.{FileUtils, JsonUtil, PhrLogger}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success, Try}

@Singleton
class ProductController @Inject()(
   cc: ControllerComponents,
   drugsService: DrugService,
   silhouette: Silhouette[JWTEnv],
   credentialsProvider: CredentialsProvider,
   userService: PhrIdentityService,
   authenticatorService: AuthenticatorService[JWTAuthenticator],
   eventBus: EventBus,
   clock: Clock,
   configuration: Configuration,
   assetsFinder: AssetsFinder,
   assets: Assets)
  (implicit ex: ExecutionContext) extends AbstractController(cc) with PhrLogger {

  import model.ModelImplicits._

  def initDB = Action.async {
    drugsService.createTextIndex().map(_ => Ok("OK"))
  }

  /**
    * Service load products from JSON file
    *
    * @return information about operation
    */
  def loadProducts = silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE))(parse.multipartFormData).async { implicit request =>
    val drugsToSave = Try[List[DrugsProduct]] (request.body.file("fileinfo").map { picture =>
      val filename = picture.filename
      val fileText = Source.fromFile(picture.ref.path.toString, "utf-8").mkString
      if (fileText.length == 0) throw new RuntimeException(s"Empty file: $filename $fileText")

      val mapValues = JsonUtil.fromJson[List[Map[String, Option[String]]]](fileText)
      mapValues.map (v => {
        DrugsProduct (
          drugsID = v("DrugsID").get,
          retailPrice = v("RetailPrice").getOrElse("0").toDouble,
          barCode = v("BarCode").getOrElse(""),
          tradeTech = v("TradeTech").getOrElse(""),
          producerFullName = v("ProducerFullName").getOrElse(""),
          drugsFullName = v("DrugsFullName").getOrElse(""),
          supplierFullName = v("SupplierFullName").getOrElse(""),
          MNN = v("MNN").getOrElse(""),
          ost = v("Ost").getOrElse("0").toDouble,
          unitFullName = v("UnitFullName").getOrElse(""),
          producerShortName = v("ProducerShortName").getOrElse(""),
          drugsShortName = v("DrugsShortName").getOrElse(""),
          packaging = v("Packaging").getOrElse(""),
          id = v("ID").get,
          unitShortName = v("UnitShortName").getOrElse("")
        )
      })
    }.get)

    drugsToSave match {
      case Success(drugs) => drugsService.bulkUpsert(drugs).flatMap(
        results => {
          val obj = results.foldLeft(UpsertRes()){
            (obj, res) => {
              UpsertRes(
                ok = obj.ok + (if (res.ok) 1 else 0),
                upserted = obj.upserted + res.upserted.size,
                modified = obj.modified + res.nModified,
                errors = obj.errors + res.writeErrors.size
              )
            }
          }

          Future.successful(Ok(Json.obj("res" -> obj)))
        }
      )
      case Failure(e) =>
        logger.error(e.getMessage, e)
        Future.successful(BadRequest(s"Error occured: ${e.getMessage}/${e.getCause}"))
    }
  }

  def setImageToDrug(drugId: String) = silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE))(parse.multipartFormData).async { implicit request =>
    val imagesFolder = configuration.get[String]("images.folder")
    val imagesBaseUrl = configuration.get[String]("images.baseUrl")
    request.body.file("image") match {
      case Some(file) =>
        val ext = FileUtils.extractExt(file.filename)
        Files.move(file.ref.path, Paths.get (s"$imagesFolder/$drugId${ext}"), StandardCopyOption.REPLACE_EXISTING)
        // Update drug information
        drugsService.addImage(drugId, s"$imagesBaseUrl/$drugId${ext}").map {
          row => Ok(Json.obj("res" -> row))
        }.recover {
          case ex => logger.error("Error setting image", ex)
            BadRequest(s"Error occured: ${ex.getMessage}/${ex.getCause}")
        }

      case _ => Future.successful(BadRequest("File not provided"))
    }
  }

  /**
    * Service searching drugs
    * @return found drugs
    */
  def combinedSearchDrugsProducts= Action(parse.json[DrugsFindRq]).async { implicit request =>
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

  def filterProducts= silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE))(parse.json[DrugsAdminRq]).async { implicit request =>
    drugsService.getAll(request.body).map (rows => Ok(Json.obj("rows" -> rows)))
  }

  def findRecommended = silhouette.UserAwareAction.async { implicit request =>
    drugsService.findRecommended.map(rows => Ok(Json.obj("rows" -> rows)))
  }

  def addRecommended (drugId: String, orderNum: Int) = silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE)).async {
    request => drugsService.addRecommended(drugId, orderNum).map(_ => Ok("OK"))
  }

  def removeRecommended (drugId: String)  = silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE)).async {
    request => drugsService.removeRecommended(drugId).map(_ => Ok("OK"))
  }
}

