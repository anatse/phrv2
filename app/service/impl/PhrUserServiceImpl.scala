package service.impl

import java.util.UUID

import javax.inject.Inject
import com.mohiva.play.silhouette.api.LoginInfo
import model.MongoBaseDao
import model.security.PhrUser
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros, document}
import service.PhrUserService
import utils.PhrLogger

import scala.concurrent.{ExecutionContext, Future}

class PhrUserServiceImpl @Inject() (val mongoApi: ReactiveMongoApi)(implicit val ex: ExecutionContext) extends PhrUserService with MongoBaseDao with PhrLogger {
  /**
    * Create users table/collection
    * @return users mongodb collections
    */
  private def usersCollection:Future[BSONCollection] = mongoApi.database.map(_.collection("users"))

  implicit def userWriter: BSONDocumentWriter[PhrUser] = Macros.writer[PhrUser]
  implicit def userReader: BSONDocumentReader[PhrUser] = Macros.reader[PhrUser]

  /**
    * Finds a PhrUser by its login info.
    *
    * @param loginInfo The login info of the PhrUser to find.
    * @return The found PhrUser or None if no PhrUser for the given login info could be found.
    */
  def find(loginInfo: LoginInfo) = {
    usersCollection.flatMap(_.find(
      document(
        "providerID" -> loginInfo.providerID,
        "providerKey" -> loginInfo.providerKey
      )
    ).one[PhrUser])
  }

  /**
    * Finds a PhrUser by its PhrUser ID.
    *
    * @param userID The ID of the PhrUser to find.
    * @return The found PhrUser or None if no PhrUser for the given ID could be found.
    */
  def find(userID: UUID) = usersCollection.flatMap(_.find(document("userID" -> userID)).one[PhrUser])

  /**
    * Saves a PhrUser.
    *
    * @param PhrUser The PhrUser to save.
    * @return The saved PhrUser.
    */
  def save(PhrUser: PhrUser) = {
    // Insert or update the PhrUser
    usersCollection.flatMap(_.update(document("userID" -> PhrUser.userID), PhrUser, upsert = true).map(_.upserted.map(ups => PhrUser) .headOption.getOrElse(PhrUser)))
  }
}
