package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.TestData
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.libs.Files.TemporaryFileCreator
import play.mvc.Http

import scala.reflect.io.File

class ProductControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting with MockitoSugar  {
  // Create application object with test config
  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder().
    loadConfig(conf = {
      val testConfig = ConfigFactory.load("application.conf")
      Configuration(testConfig)
    }).
    build()

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "Product controller" should {
    "Authenticate request using JWT token" in {

      val controller = inject[ProductController]
      val file = TemporaryFile("goods.json")
      val part = FilePart("fileinfo", "fileinfo", None, file)
      val files = Seq[FilePart[TemporaryFile]](part)

      val multipartBody = MultipartFormData[TemporaryFile](Map[String, Seq[String]](), files, Seq[BadPart]())
      val request = FakeRequest("POST", routes.ProductController.loadProducts().url)
                      .withMultipartFormDataBody(multipartBody)
                      .withHeaders(Http.HeaderNames.CONTENT_TYPE -> "multipart/form-data")

      val loadProducts = controller.loadProducts.apply(request)
      println (loadProducts)

      status(loadProducts) mustBe OK
    }
  }
}
