package service.security

import java.util.UUID

import com.google.inject.ImplementedBy
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import model.security.PhrUser

import scala.concurrent.Future

@ImplementedBy(classOf[PhrIdentityServiceImpl])
trait PhrIdentityService extends IdentityService[PhrUser] {

  /**
    * Retrieves a user that matches the specified ID.
    *
    * @param id The ID to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given ID.
    */
  def retrieve(id: UUID): Future[Option[PhrUser]]

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: PhrUser): Future[PhrUser]

  /**
    * Saves the social profile for a user.
    *
    * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
    *
    * @param profile The social profile to save.
    * @return The user for whom the profile was saved.
    */
  def save(profile: CommonSocialProfile): Future[PhrUser]

  /**
    * Retrieves user that matches the specified name
    * @param loginInfo The login info contained name to retrieve a user
    * @return The retrieved user or None if no user could be retrieved for the given  name
    */
  def retrieveByName(loginInfo: LoginInfo) : Future[Option[PhrUser]]
}