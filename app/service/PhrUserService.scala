package service

import java.util.UUID

import com.google.inject.ImplementedBy
import com.mohiva.play.silhouette.api.LoginInfo
import model.security.PhrUser
import service.impl.PhrUserServiceImpl

import scala.concurrent.Future

@ImplementedBy(classOf[PhrUserServiceImpl])
trait PhrUserService {
  /**
    * Finds a user by its login info.
    *
    * @param loginInfo The login info of the user to find.
    * @return The found user or None if no user for the given login info could be found.
    */
  def find(loginInfo: LoginInfo): Future[Option[PhrUser]]

  /**
    * Finds a user by its user ID.
    *
    * @param userID The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  def find(userID: UUID): Future[Option[PhrUser]]

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: PhrUser): Future[PhrUser]
}
