package service.impl

import com.google.inject.Inject
import model.{DrugExcelRecord, DrugsProduct, MongoBaseDao, RecommendedDrugs}
import play.api.cache.{NamedCache, SyncCacheApi}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros, document}
import service.DrugImportService

import scala.concurrent.{ExecutionContext, Future}

class DrugImportServiceImpl @Inject() (val mongoApi: ReactiveMongoApi, @NamedCache("user-cache")cacheApi: SyncCacheApi, implicit val ex: ExecutionContext) extends MongoBaseDao with DrugImportService {
  private def drugExcelCollection:Future[BSONCollection] = mongoApi.database.map(_.collection("drugexcel"))

  implicit def deWriter: BSONDocumentWriter[DrugExcelRecord] = Macros.writer[DrugExcelRecord]
  implicit def deReader: BSONDocumentReader[DrugExcelRecord] = Macros.reader[DrugExcelRecord]

  override def bulkSaveExcelRecords(entities: List[DrugExcelRecord]): Future[Unit] = drugExcelCollection.flatMap(
    col => {
      val bulkDocs = entities.map(implicitly[col.ImplicitlyDocumentProducer](_))
      col.insert[DrugExcelRecord](ordered = true).many(entities)
    }
  ).recover {
    case ex => logger.error(ex.toString, ex)
  }.map(_ => {})

  override def findAll: Future[List[DrugExcelRecord]] = drugExcelCollection.flatMap(
    _.find(document()).cursor[DrugExcelRecord]()
      .collect[List](-1, handler[DrugExcelRecord])
  )

  override def clearCollection: Future[Unit] = drugExcelCollection.flatMap(_.remove(document()).map(_ => {}))

  override def findByName(drugName: String): Future[Option[DrugExcelRecord]] = drugExcelCollection.flatMap(
    _.find(document("tradeName" -> document("$regex" -> s"${drugName}.*", "$options" -> "i")))
      .one[DrugExcelRecord]
  )
}
