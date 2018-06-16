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
import model.{DrugsFindRq, DrugsProduct}
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
import scala.io.{Source => ScalaSource}

case class DrugRes (rows: List[DrugsProduct], pageSize: Int, offset: Int, hasMore: Boolean)

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
  var jwtTokenTestUser: String = _

  private val DRUG_ID = "1-1500-3691"
  "Product & Security controllers" must {
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

    "initdb initialize database" in {
      val responseFuture = wsClient.url(s"$url/initdb").post("")
      val response = await(responseFuture)
      response.status mustBe OK
    }

    "upload a file successfully" in {
      val tmpFile = java.io.File.createTempFile("prefix", "txt")
      tmpFile.deleteOnExit()
      val msg = """[{"RetailPrice":"1500.00","BarCode":"200100000001.00","TradeTech":null,"ProducerFullName":"Betafarma",
          |"DrugsFullName":"ХАЙР ВИТАЛ шампунь против выпаден 200мл","SupplierFullName":"Протек","MNN":null,
          |"Ost":"0.0","UnitFullName":"штука","ProducerShortName":"Betafarma","DrugsShortName":"ХАЙР ВИТ. шамп.  200мл против вып",
          |"Packaging":null,"Fas":null,"ID":"1-1500-3691","UnitShortName":"шт.","DrugsID":"1"},
          |{"RetailPrice":"1500.00","BarCode":"200100000021.00","TradeTech":null,"ProducerFullName":"Betafarma",
          |"DrugsFullName":"Другое лекарство","SupplierFullName":"Протек","MNN":null,
          |"Ost":"0.0","UnitFullName":"штука","ProducerShortName":"Betafarma","DrugsShortName":"Другое лекарство",
          |"Packaging":null,"Fas":null,"ID":"2-1500-3691","UnitShortName":"шт.","DrugsID":"2"}]""".stripMargin

      Files.write(tmpFile.toPath, msg.getBytes())

      val responseFuture = wsClient.url(s"$url/goods/upload").withHttpHeaders("X-Auth-Token" -> jwtToken).post(postSource(tmpFile))
      val response = await(responseFuture)
      response.status mustBe OK
      response.body mustEqual """{"res":{"ok":2,"upserted":2,"modified":0,"errors":0}}"""
    }

    "unauthorized user must find product using full name" in {
      val rq = DrugsFindRq (
        groups = None,
        text = Some("ХАЙР ВИТАЛ шампунь против выпаден 200мл"),
        sorts = None,
        hasImage = 0,
        offset = 0,
        pageSize = 10
      )
      val responseFuture = wsClient.url(s"$url/drugs/fuzzySearch")
        .addHttpHeaders("Content-Type" -> "application/json")
        .post(postObject(rq))

      val response = await(responseFuture)
      response.status mustBe OK

      val foundDrugs = mapper.readValue(response.body, classOf[DrugRes])
      foundDrugs.rows.size mustBe 1
      foundDrugs.hasMore mustBe false
      foundDrugs.rows(0).id mustEqual DRUG_ID
    }

    "unauthorized user must find product using one word" in {
      val rq = DrugsFindRq (
        groups = None,
        text = Some("шампунь"),
        sorts = None,
        hasImage = 0,
        offset = 0,
        pageSize = 10
      )
      val responseFuture = wsClient.url(s"$url/drugs/fuzzySearch")
        .addHttpHeaders("Content-Type" -> "application/json")
        .post(postObject(rq))

      val response = await(responseFuture)
      response.status mustBe OK

      val foundDrugs = mapper.readValue(response.body, classOf[DrugRes])
      foundDrugs.rows.size mustBe 1
      foundDrugs.hasMore mustBe false
      foundDrugs.rows(0).id mustEqual DRUG_ID
    }

    "unauthorized user must find product using wrong spelled word" in {
      val rq = DrugsFindRq (
        groups = None,
        text = Some("шомпунь пратев"),
        sorts = None,
        hasImage = 0,
        offset = 0,
        pageSize = 10
      )
      val responseFuture = wsClient.url(s"$url/drugs/fuzzySearch")
        .addHttpHeaders("Content-Type" -> "application/json")
        .post(postObject(rq))

      val response = await(responseFuture)
      response.status mustBe OK

      val foundDrugs = mapper.readValue(response.body, classOf[DrugRes])
      foundDrugs.rows.size mustBe 1
      foundDrugs.hasMore mustBe false
      foundDrugs.rows(0).id mustEqual DRUG_ID
    }

    "register new user" in {
      val responseFuture = wsClient.url(s"$url/register")
        .addQueryStringParameters(
          "userName" -> "test",
          "password" -> "1234",
          "email" -> "test@test.com",
          "fullName" -> "mr. Test"
        ).get()

      val response = await(responseFuture)
      response.status mustBe OK
      response.body mustEqual "Registered"
    }

    "authenticate new created user" in {
      val responseFuture = wsClient.url(s"$url/auth")
        .addQueryStringParameters("userName" -> "test", "password" -> "1234")
        .get()

      val response = await(responseFuture)
      response.status mustBe OK

      val json = mapper.readValue(response.body, classOf[Map[String, String]])
      json.contains("token") mustBe true

      jwtTokenTestUser = json("token")
    }

    "add roles to user" in {
      val responseFuture = wsClient.url(s"$url/user/test/roles")
        .withHttpHeaders("X-Auth-Token" -> jwtToken, "Content-Type" -> "application/json")
        .post("""["MANAGER", "LABORER"]""")

      val response = await(responseFuture)
      response.status mustBe OK
      response.body mustEqual "current roles: MANAGER,LABORER"
    }

    "user w/o ADMIN role not allowed to add roles to user" in {
      val responseFuture = wsClient.url(s"$url/user/test/roles")
        .withHttpHeaders("X-Auth-Token" -> jwtTokenTestUser, "Content-Type" -> "application/json")
        .post("""["ADMIN"]""")

      val response = await(responseFuture)
      response.status mustBe UNAUTHORIZED
    }

    "administrator may add image to drug" in {
      val resource ="/noimage.png"
      val responseFuture = wsClient.url(s"$url/drugs/1/image")
        .withHttpHeaders("X-Auth-Token" -> jwtToken)
        .post(postImageSource(() => getClass.getResourceAsStream(resource)))

      val response = await(responseFuture)
      response.status mustBe OK
    }

    var imageUrl:String = ""
    "request drug's information with image should return one row" in {
      val rq = DrugsFindRq (
        groups = None,
        text = Some("шампунь"),
        sorts = None,
        hasImage = 1,
        offset = 0,
        pageSize = 10
      )
      val responseFuture = wsClient.url(s"$url/drugs/fuzzySearch")
        .addHttpHeaders("Content-Type" -> "application/json")
        .post(postObject(rq))

      val response = await(responseFuture)
      response.status mustBe OK

      val foundDrugs = mapper.readValue(response.body, classOf[DrugRes])
      foundDrugs.rows.size mustBe 1
      foundDrugs.hasMore mustBe false
      foundDrugs.rows(0).drugImage must not be None
      imageUrl = foundDrugs.rows(0).drugImage.get
    }

    "administrator can add recommended drugs" in {
      val responseFuture = wsClient.url(s"$url/drugs/recom/add")
        .addQueryStringParameters("drugId" -> "2-1500-3691", "orderNum" -> "1")
        .withHttpHeaders("X-Auth-Token" -> jwtToken, "Content-Type" -> "application/json")
        .post("")

      val response = await(responseFuture)
      response.status mustBe OK
      response.body must be equals "OK"
    }

    "user w/o admin privilegies cannot add recommended drugs" in {
      val responseFuture = wsClient.url(s"$url/drugs/recom/add")
        .addQueryStringParameters("drugId" -> "2-1500-3691", "orderNum" -> "1")
        .withHttpHeaders("X-Auth-Token" -> jwtTokenTestUser, "Content-Type" -> "application/json")
        .post("")

      val response = await(responseFuture)
      response.status mustBe UNAUTHORIZED
      response.body must be equals "Not authorized"
    }

    "unauthorized user get list of recommended drugs" in {
      val responseFuture = wsClient.url(s"$url/drugs/recom").post("")

      val response = await(responseFuture)
      response.status mustBe OK

      val foundDrugs = mapper.readValue(response.body, classOf[DrugRes])
      foundDrugs.rows.size mustBe 1
      foundDrugs.hasMore mustBe false
      foundDrugs.rows(0).id mustEqual "2-1500-3691"
    }

    "administrator can remove recommended drugs" in {
      val responseFuture = wsClient.url(s"$url/drugs/recom/rm")
        .addQueryStringParameters("drugId" -> "2-1500-3691")
        .withHttpHeaders("X-Auth-Token" -> jwtToken, "Content-Type" -> "application/json")
        .post("")

      val response = await(responseFuture)
      response.status mustBe OK
      response.body must be equals "OK"
    }


    // TODO make this test working
//    "image should be accessible using url" in {
//      val responseFuture = wsClient.url(s"$url/$imageUrl").get()
//      println (s"Image: $url/$imageUrl")
//      val response = await(responseFuture)
//      response.status mustBe OK
//    }
  }

  private final def postObject(obj: AnyRef) = {
    mapper.writeValueAsString(obj)
  }

  private final def postImageSource(inputStream:() => InputStream): Source[MultipartFormData.Part[Source[ByteString, _]], _] = {
    import play.api.mvc.MultipartFormData._
    Source(FilePart("image", "image.png", Option("image/png"), StreamConverters.fromInputStream(inputStream)) :: DataPart("key", "value") :: List())
  }

  private final def postSource(tmpFile: File): Source[MultipartFormData.Part[Source[ByteString, _]], _] = {
    import play.api.mvc.MultipartFormData._
    Source(FilePart("fileinfo", "goods.json", Option("application/json"), FileIO.fromPath(tmpFile.toPath)) :: DataPart("key", "value") :: List())
  }

  private final def readResource(resource: String) = {
    val bis = new BufferedInputStream(getClass.getResourceAsStream(resource))
    try Stream.continually(bis.read).takeWhile(-1 !=).map(_.toByte).toArray
    finally bis.close()
  }
}
