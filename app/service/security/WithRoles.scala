package service.security

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import model.security.PhrUser
import play.api.mvc.Request

import scala.concurrent.Future

case class WithRoles(roles: String*) extends Authorization[PhrUser, JWTAuthenticator] {
  def isAuthorized[B](user: PhrUser, authenticator: JWTAuthenticator)(
    implicit request: Request[B]) = {
    Future.successful(user.roles match {
      case Some(r) => !roles.intersect(r).isEmpty
      case _ => false
    })
  }
}
