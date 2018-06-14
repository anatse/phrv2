package controllers

import java.io._
import java.nio.file.Files
import java.util.UUID

import akka.stream.scaladsl._
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.typesafe.config.ConfigFactory
import emb.EmbeddedMongo
import model.security.PhrUser
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Configuration}
import service.impl.PhrAuthInfoService
import service.security.PhrIdentityService

import scala.concurrent.Await
import scala.concurrent.duration._

class ProductControllerSpec extends PlaySpec with GuiceOneServerPerSuite with Injecting {

  val identity = PhrUser(
    userID = UUID.randomUUID(),
    providerID = Some("credentials"),
    providerKey = Some("demo"),
    firstName = None,
    lastName = None,
    fullName = None,
    email = Some("demo@demo.com"),
    avatarURL = None,
    activated = true,
    roles = Some(Array("ADMIN")))

    override def fakeApplication(): Application =
      new GuiceApplicationBuilder()
        .loadConfig(conf = {
            val testConfig = ConfigFactory.load("application.test.conf")
            Configuration(testConfig)
          })
        .build()

  // Start embedded mongo
  println ("mongo starting...")

  val mongoServer = new EmbeddedMongo()
  mongoServer.start

  println ("mongo started")

  "UserService" must {
    "add demo user to database" in {
      val userService = inject[PhrIdentityService]
      val savedUserFuture = userService.save(identity)
      val savedUser = Await.result(savedUserFuture, 1 second)

      savedUser.providerID mustBe identity.providerID
      savedUser.providerKey mustBe identity.providerKey
      savedUser.userID mustBe identity.userID

      // save password information
      val authInfo = inject[PhrAuthInfoService]
      val passwordHasherRegistry = inject[PasswordHasherRegistry]

      val passwordInfo = passwordHasherRegistry.current.hash("123456")
      val passwordFuture = authInfo.add(LoginInfo(savedUser.providerID.get, savedUser.providerKey.get), passwordInfo)
      val savedPwdInfo = Await.result(passwordFuture, 1 second)

      savedPwdInfo.password mustNot be (null)
    }
  }

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  val url = s"http://localhost:${Helpers.testServerPort}"
  var jwtToken:String = _

  "ProductController" must {
    val wsClient = inject[WSClient]

    "authorize user using credentials" in {
      val responseFuture = wsClient.url(s"$url/auth")
        .addQueryStringParameters("userName" -> "demo", "password" -> "123456")
        .get()

      val response = await(responseFuture)
      response.status mustBe OK

      val json = mapper.readValue(response.body, classOf[Map[String, String]])
      json.contains("token") mustBe true

      jwtToken = json("token")
    }

    "upload a file successfully" in {
      val tmpFile = java.io.File.createTempFile("prefix", "txt")
      tmpFile.deleteOnExit()
      val msg = """[{"RetailPrice":"1500.00","BarCode":"200100000001.00","TradeTech":null,"ProducerFullName":"Betafarma",
          |"DrugsFullName":"ХАЙР ВИТАЛ шампунь против выпаден 200мл","SupplierFullName":"Протек","MNN":null,
          |"Ost":"0.0","UnitFullName":"штука","ProducerShortName":"Betafarma","DrugsShortName":"ХАЙР ВИТ. шамп.  200мл против вып",
          |"Packaging":null,"Fas":null,"ID":"1-1500-3691","UnitShortName":"шт.","DrugsID":"1"}""".stripMargin

      Files.write(tmpFile.toPath, msg.getBytes())

      val responseFuture = wsClient.url(s"$url/goods/upload").withHttpHeaders("X-Auth-Token" -> jwtToken).post(postSource(tmpFile))
      val response = await(responseFuture)
      response.status mustBe OK
      response.body mustBe "file size = 11"
    }
  }

  def postSource(tmpFile: File): Source[MultipartFormData.Part[Source[ByteString, _]], _] = {
    import play.api.mvc.MultipartFormData._
    Source(FilePart("name", "hello.txt", Option("text/plain"),
    FileIO.fromPath(tmpFile.toPath)) :: DataPart("key", "value") :: List())
  }
}
