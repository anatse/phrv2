package controllers

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, Credentials}
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.nimbusds.jose.crypto.MACVerifier
import javax.inject.{Inject, Singleton}
import model._
import model.security.JWTEnv
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import play.core.parsers.Multipart
import service.{DrugService, PhrUserService}
import service.security.{PhrIdentityService, WithRoles}
import utils.{JsonUtil, PhrLogger}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}
import scala.io.Source
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProductController @Inject()(
   cc: ControllerComponents,
   drugsService: DrugService,
   silhouette: Silhouette[JWTEnv],
   credentialsProvider: CredentialsProvider,
   userService: PhrIdentityService,
   authenticatorService: AuthenticatorService[JWTAuthenticator],
   eventBus: EventBus,
   clock: Clock)
  (implicit ex: ExecutionContext) extends AbstractController(cc) with PhrLogger {

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

  def auth(userName: String, password: String) = silhouette.UnsecuredAction.async { implicit request =>
    val credentials = Credentials(userName, password)
    val auth = credentialsProvider.authenticate(credentials)
    auth.flatMap { loginInfo =>
      userService.retrieve(loginInfo).flatMap {
        case Some(user) => authenticatorService.create(loginInfo).map { authenticator =>
          authenticator
        }.flatMap {
          authenticator => {
            eventBus.publish(LoginEvent(user, request))
            authenticatorService.init(authenticator).map {
              token =>
//                JWTAuthenticator.unserialize()
                Ok(Json.obj("token" -> token))

            }
          }
        }

        case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
      }
    }.recover {
      case ex: ProviderException =>
        ex.printStackTrace()
        Unauthorized("error: invalid.credentials")
    }
  }

  def loadProducts = Action.async { implicit request =>
    silhouette.SecuredRequestHandler { securedRequest =>
      Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
    }.map {
      case HandlerResult(r, Some(user)) => Ok("OK")
      case HandlerResult(r, None) => Unauthorized
    }
  }

  def loadProducts1 = silhouette.SecuredAction(WithRoles("ADMIN"))(parse.multipartFormData).async { implicit request =>
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
}

