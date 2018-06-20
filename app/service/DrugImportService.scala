package service

import com.google.inject.ImplementedBy
import model.DrugExcelRecord
import reactivemongo.api.commands.MultiBulkWriteResult
import service.impl.DrugImportServiceImpl

import scala.concurrent.Future

@ImplementedBy(classOf[DrugImportServiceImpl])
trait DrugImportService {
  def bulkSaveExcelRecords(entities: List[DrugExcelRecord]): Future[Unit]
  def findAll: Future[List[DrugExcelRecord]]
  def clearCollection: Future[Unit]
  def findByName (drugName: String): Future[Option[DrugExcelRecord]]
}
