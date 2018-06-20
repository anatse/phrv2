package actors

import java.io.{BufferedInputStream, FileOutputStream, InputStream}
import java.net.{HttpURLConnection, URL}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipEntry, ZipInputStream}

import akka.NotUsed
import akka.actor.{Actor, Props}
import akka.dispatch.Futures
import akka.event.Logging
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import javax.inject.Inject
import model.DrugExcelRecord
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import play.api.Configuration
import service.{DrugImportService, DrugService}
import utils.PhrLogger
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import org.apache.poi.ss.usermodel.{Cell, CellType, Row, WorkbookFactory}

import scala.concurrent.{Await, Future}
import scala.util.Try
import model.DrugsFindRq

class LoadRls @Inject() (configuration: Configuration, drugImportService: DrugImportService, drugService: DrugService) extends Actor with PhrLogger {
  val log = Logging(context.system, this)
  val workerActorCount = configuration.get[Int]("rlsLoader.workActor.count")
  val rosminzdrav = "http://grls.rosminzdrav.ru/grls.aspx"
  val baseRmUrl = "http://grls.rosminzdrav.ru/"

  private final def makeLocalRouter = {
    val routes = Vector.fill(workerActorCount) {
      val worker = context.actorOf(RlsWorker.props)
      context watch worker
      ActorRefRoutee (worker)
    }

    Router(RoundRobinRoutingLogic(), routes)
  }

  private final def readInputStream(is: InputStream) = {
    Stream.continually(is.read).takeWhile(-1 !=).map(_.toByte).toArray
  }

  import RlsUtils._

  private final def loadExcel = {
    import context.dispatcher
    import concurrent.duration._
    import scala.collection.JavaConverters._

    val browser = JsoupBrowser()
    val doc = browser.get(rosminzdrav)
    val excelLink = doc >> element ("#ctl00_plate_tdzip a") attr ("href")
    val url = new URL(baseRmUrl + excelLink)
    Try {
        val excelFileStream = url.openConnection().asInstanceOf[HttpURLConnection].getInputStream
        val zis = new ZipInputStream(excelFileStream)
        var ze: ZipEntry = zis.getNextEntry()
        if (ze != null) {
          val wb = WorkbookFactory.create(zis)
          val sheet = wb.getSheet("grls2")

          Await.result(drugImportService.clearCollection, 10 second)

          val rowIterator: List[Row] = sheet.rowIterator().asScala.toList
          val futures = rowIterator.filter(_.getRowNum > 1).map { row =>
            val drugRecord = DrugExcelRecord(
              regNum = row.getString(REG_NUM),
              regDate = row.getDate(REG_DATE).getOptionTime,
              regExpiredDate = row.getDate(REG_EXPIRED_DATE).getOptionTime,
              regInvalidDate = row.getDate(REG_INVALID_DATE).getOptionTime,
              regOwner = row.getString(REG_OWNER),
              regCountry = row.getString(REG_OWNER_COUNTRY),
              tradeName = row.getString(TRADE_NAME),
              mnn = row.getString(MNN),
              forms = row.getString(FORM),
              prodStage = row.getString(PROD_STAGES),
              barCodes = row.getString(BAR_CODES),
              normDocs = row.getString(NORM_DOC),
              group = row.getString(GROUP)
            )

            drugImportService.bulkSaveExcelRecords(List(drugRecord))
          }

          wb.close()
          Await.result(Future.sequence(futures), 30 second)
        }
    }.recover {
      case ex => logger.error(ex.toString, ex)
    }
  }

  private final def connectDrug(drugName: String) = {
    import context.dispatcher

    drugService.combinedSearch(DrugsFindRq (text = Some(drugName), hasImage = -1, offset = 0, pageSize = 10))
      .flatMap {res =>
        Future.sequence(res.map { drug =>
          val text = drug.drugsFullName.split(" ")
          drugImportService.findByName(text(0)).map {
            case Some(di) => drug.copy(drugGroups = Some(Array(di.group)))
            case _ => drug
          }
        })
      }.flatMap(dg => Future.sequence(dg.map (drugService.save(_))))
  }

  override def receive: Receive = {
    case LoadDrug(drugName) =>

    case LoadFromSite => loadExcel // Load excel file into Source stream

    case ConnectDrug(drugName) => connectDrug(drugName) // Add groups from drugexcel to drug
  }
}

object LoadRls {
  def props(configuration: Configuration, drugImportService: DrugImportService, drugService: DrugService) = Props(new LoadRls(configuration, drugImportService, drugService))
}