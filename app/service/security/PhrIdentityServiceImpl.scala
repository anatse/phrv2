package service.security

import java.util.UUID

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import model.security.PhrUser
import service.PhrUserService

import scala.concurrent.{ExecutionContext, Future}

class PhrIdentityServiceImpl @Inject()(userService: PhrUserService)(implicit ec:ExecutionContext) extends PhrIdentityService {
  override def retrieve(loginInfo: LoginInfo): Future[Option[PhrUser]] = userService.find(loginInfo)

  def retrieve(id: UUID) = userService.find(id)
  def save(PhrUser: PhrUser) = userService.save(PhrUser)
  def save(profile: CommonSocialProfile) = {
    userService.find(profile.loginInfo).flatMap {
      case Some(user) => // Update PhrUser with profile
        userService.save(user.copy(
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email,
          avatarURL = profile.avatarURL))

      case None => // Insert a new PhrUser
        userService.save(PhrUser(
          userID = UUID.randomUUID(),
          providerID = Some(profile.loginInfo.providerID),
          providerKey = Some(profile.loginInfo.providerKey),
          firstName = profile.firstName,
          lastName = profile.lastName,
          fullName = profile.fullName,
          email = profile.email,
          avatarURL = profile.avatarURL,
          activated = true,
          roles = None))
    }
  }

  /**
    * Retrieves user that matches the specified name
    *
    * @param loginInfo The login info contained name to retrieve a user
    * @return The retrieved user or None if no user could be retrieved for the given  name
    */
  override def retrieveByName(loginInfo: LoginInfo) = {
    userService.find(loginInfo)
  }
}
