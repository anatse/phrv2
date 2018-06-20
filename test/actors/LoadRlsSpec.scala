package actors

import java.io.FileOutputStream
import java.net.{HttpURLConnection, URL}
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.actor.{ActorRef, ActorSystem}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Injecting
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import play.api.Configuration
import service.{DrugImportService, DrugService}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import concurrent.duration._

class LoadRlsSpec extends PlaySpec with GuiceOneServerPerSuite with Injecting {
  val system = ActorSystem("MySpec")

  "LoadRls" should {
    val config = inject[Configuration]
    val drugService = inject[DrugService]
    val drugImportService = inject[DrugImportService]
    val rls = system.actorOf(LoadRls.props(config, drugImportService, drugService))

    implicit val timeout = Timeout(10 second)

//    "downloads XSL file from Rosminzdrav" in {
//      rls ? LoadFromSite
//
//      // load file from url
//      val url = new URL("http://grls.rosminzdrav.ru/GetGRLS.ashx?FileGUID=9c7a70c5-46e8-456a-9113-8c3ce691de35&UserReq=9994146")
//      val excelFileStream = url.openConnection().asInstanceOf[HttpURLConnection].getInputStream
//      val zis = new ZipInputStream(excelFileStream)
//      var ze: ZipEntry = zis.getNextEntry()
//      if (ze != null) {
//        val fos = new FileOutputStream("out.xlsx");
//
//        var buffer = new Array[Byte](8192)
//        var len: Int = zis.read(buffer)
//        var size: Int = 0
//
//        while (len > 0) {
//          size += len
//          println(s"read: $len, size: $size")
//          fos.write(buffer, 0, len)
//          len = zis.read(buffer)
//        }
//
//        fos.close()
//      }
//    }
  }
}
