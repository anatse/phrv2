package service.security

import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results.Unauthorized

import scala.concurrent.Future

class PhrUnsecuredErrorHandlerImpl extends UnsecuredErrorHandler {
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful(Unauthorized("Not authorized"))
  }
}
