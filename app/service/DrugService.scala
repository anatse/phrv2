package service

import com.google.inject.ImplementedBy
import model._
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import service.impl.DrugServiceImpl

import scala.concurrent.Future

@ImplementedBy(classOf[DrugServiceImpl])
trait DrugService {
  def getAll(dp:DrugsAdminRq): Future[List[DrugsProduct]]

  def combinedSearch (filter:DrugsFindRq): Future[List[DrugsProduct]]

  // Admin functions
  def createTextIndex ():Future[WriteResult]
  def bulkInsert (entities: List[DrugsProduct]): Future[Unit]
  def bulkUpsert (entities: List[DrugsProduct]): Future[Seq[UpdateWriteResult]]

  // Product update functions
  def addImage (id: String, imageUrl: String):Future[Option[DrugsProduct]]
  def setGroups (id: String, groups: Array[String]):Future[Option[DrugsProduct]]

  def findRecommended: Future[List[DrugsProduct]]
  def addRecommended (drugId: String, orderNum: Int): Future[Unit]
  def removeRecommended (drugId: String): Future[Unit]

  def findById (id: String): Future[Option[DrugsProduct]]
  def save (entity: DrugsProduct): Future[DrugsProduct]
  def remove(id: String): Future[Unit]
  def findAll(sortField: Option[String], offset: Int, pageSize: Int): Future[List[DrugsProduct]]
}
