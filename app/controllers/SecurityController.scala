package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.api.{EventBus, LoginEvent, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.Inject
import model.security.{JWTEnv, PhrUser}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import service.DrugService
import service.impl.PhrAuthInfoService
import service.security.{PhrIdentityService, WithRoles}
import utils.PhrLogger

import scala.concurrent.{ExecutionContext, Future}

class SecurityController @Inject()(
    cc: ControllerComponents,
    drugsService: DrugService,
    silhouette: Silhouette[JWTEnv],
    credentialsProvider: CredentialsProvider,
    userService: PhrIdentityService,
    authInfoService: PhrAuthInfoService,
    passwordHasherRegistry: PasswordHasherRegistry,
    authenticatorService: AuthenticatorService[JWTAuthenticator],
    eventBus: EventBus,
    clock: Clock)
  (implicit ex: ExecutionContext) extends AbstractController(cc) with PhrLogger {

  def auth(userName: String, password: String) = silhouette.UnsecuredAction.async { implicit request =>
    logger.debug(s"enter auth userName: ${userName}")

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
              token => Ok(Json.obj("token" -> token))
            }
          }
        }

        case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
      }
    }.recover {
      case ex: ProviderException =>
        logger.error("error auth", ex)
        Unauthorized("error: invalid.credentials")
    }
  }

  def register(userName: String,
               password: String,
               email: String,
               firstName: Option[String] = None,
               lastName: Option[String] = None,
               fullName: Option[String] = None,
               avatarUrl: Option[String] = None) = silhouette.UnsecuredAction.async { implicit request =>
    logger.debug(s"enter register userName: ${userName}")

    credentialsProvider.loginInfo(Credentials(userName, password)).flatMap { loginInfo =>
      userService.save(PhrUser(
        userID = UUID.randomUUID(),
        providerID = Some(loginInfo.providerID),
        providerKey = Some(loginInfo.providerKey),
        firstName = firstName,
        lastName = lastName,
        fullName = fullName,
        email = Some(email),
        avatarURL = avatarUrl,
        activated = true,
        roles = None)).flatMap { user =>
          authInfoService.add(LoginInfo(user.providerID.get, user.providerKey.get), passwordHasherRegistry.current.hash(password)).flatMap {
            pwdInfo => Future.successful(Ok("Registered"))
          }
      }
    }.recover {
      case ex: ProviderException =>
        logger.error("error register", ex)
        NotAcceptable("error: Not registered")
    }
  }

  def setRolesToUser(userName: String) = silhouette.SecuredAction(WithRoles(Roles.ADMIN_ROLE))(parse.json[Array[String]]).async { implicit request =>
    credentialsProvider.loginInfo(Credentials(userName, "")).flatMap { loginInfo =>
      userService.retrieveByName(loginInfo).flatMap { userOption =>
        userOption match {
          case Some(user) =>
            userService.save(user.copy(roles = Some(request.body))).flatMap { user =>
              val rolesStr = user.roles.getOrElse(Array[String]()).mkString(",")
              Future.successful(Ok(s"current roles: ${rolesStr}"))
            }

          case _ => Future.successful(NotAcceptable("error: Not registered"))
        }
      }
    }.recover {
      case ex =>
        logger.error("error register", ex)
        NotAcceptable("error: Not registered")
    }
  }
}
