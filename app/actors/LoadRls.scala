package actors

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import javax.inject.Inject
import model.{DrugExcelRecord, DrugsFindRq}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import org.apache.poi.ss.usermodel.{Row, WorkbookFactory}
import play.api.Configuration
import service.{DrugImportService, DrugService}
import utils.PhrLogger

import scala.concurrent.{Await, Future}
import scala.util.Try

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

  private final def loadExcel(observer: Option[ActorRef]) = {
    import context.dispatcher

    import concurrent.duration._
    import scala.collection.JavaConverters._

    observer.map(_ ! LoadedDrug("Loading..."))

    val browser = JsoupBrowser()
    val doc = browser.get(rosminzdrav)
    val excelLink = doc >> element ("#ctl00_plate_tdzip a") attr ("href")

    observer.map(_ ! LoadedDrug(s"excel link: ${excelLink}"))

    val url = new URL(baseRmUrl + excelLink)
    Try {
        val excelFileStream = url.openConnection().asInstanceOf[HttpURLConnection].getInputStream
        val zis = new ZipInputStream(excelFileStream)
        var ze: ZipEntry = zis.getNextEntry()

        if (ze != null) {
          observer.map(_ ! LoadedDrug(s"zip file: ${ze.getName}"))

          val wb = WorkbookFactory.create(zis)
          val sheet = wb.getSheet("grls2")

          observer.map(_ ! LoadedDrug(s"Trying to remove all drugs info..."))

          Await.result(drugImportService.clearCollection, 10 second)

          observer.map(_ ! LoadedDrug(s"Removed"))

          val rowIterator: List[Row] = sheet.rowIterator().asScala.toList
          observer.map(_ ! LoadedDrug(s"Start reading excel..."))

          rowIterator.filter(_.getRowNum > 1).map { row =>
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

            // send information to observer
            observer.foreach(_ ! LoadedDrug(drugRecord.tradeName.getOrElse("<Empty>")))
            Await.result(drugImportService.bulkSaveExcelRecords(List(drugRecord)), 1 second)
          }

          wb.close()
        }
    }.recover {
      case ex => ex.printStackTrace()
        logger.error(ex.toString, ex)
    }
  }

  private final def connectDrug(drugName: String) = {
    import context.dispatcher

    drugService.combinedSearch(DrugsFindRq (text = Some(drugName), hasImage = -1, offset = 0, pageSize = 10))
      .flatMap {res =>
        Future.sequence(res.map { drug =>
          val text = drug.drugsFullName.split(" ")
          drugImportService.findByName(text(0)).map {
            case Some(di) => drug.copy(drugGroups = Some(Array(di.group.get)))
            case _ => drug
          }
        })
      }.flatMap(dg => Future.sequence(dg.map (drugService.save(_))))
  }

  override def receive: Receive = {
    case LoadDrug(drugName) =>

    case LoadFromSite(observer) => loadExcel(observer) // Load excel file into Source stream

    case ConnectDrug(drugName) => connectDrug(drugName) // Add groups from drugexcel to drug
  }
}

object LoadRls {
  def props(configuration: Configuration, drugImportService: DrugImportService, drugService: DrugService) = Props(new LoadRls(configuration, drugImportService, drugService))
}