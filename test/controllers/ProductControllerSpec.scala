package controllers

import java.io._
import java.nio.file.Files
import java.util.Date

import akka.stream.scaladsl._
import akka.util.ByteString
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

class ProductControllerSpec extends PlaySpec with GuiceOneServerPerSuite with Injecting {

    "HomeController" must {
      "upload a file successfully" in {
        val tmpFile = java.io.File.createTempFile("prefix", "txt")
        tmpFile.deleteOnExit()
        val msg = """[{"RetailPrice":"1500.00","BarCode":"200100000001.00","TradeTech":null,"ProducerFullName":"Betafarma",
            |"DrugsFullName":"ХАЙР ВИТАЛ шампунь против выпаден 200мл","SupplierFullName":"Протек","MNN":null,
            |"Ost":"0.0","UnitFullName":"штука","ProducerShortName":"Betafarma","DrugsShortName":"ХАЙР ВИТ. шамп.  200мл против вып",
            |"Packaging":null,"Fas":null,"ID":"1-1500-3691","UnitShortName":"шт.","DrugsID":"1"}""".stripMargin

        Files.write(tmpFile.toPath, msg.getBytes())

        val url = s"http://localhost:${Helpers.testServerPort}/goods/upload"
        val jwt = genJWT("anatolse", "rph", "tolik", 10000, "1s`20s2deE$r;Io]w^fpx:x^HEQ9eyeF7H:MS10iGOxdh1GH5p/hDw=Sq[ieK]Ndwfg")
        val responseFuture = inject[WSClient].url(url).withHttpHeaders("X-Auth-Token" -> jwt).post(postSource(tmpFile))
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

  def genJWT (id: String, issuer: String, subject: String, ttl: Long, apiSecret: String) = {
    import io.jsonwebtoken.JwtBuilder
    import io.jsonwebtoken.Jwts
    import io.jsonwebtoken.SignatureAlgorithm
    import javax.crypto.spec.SecretKeySpec
    import javax.xml.bind.DatatypeConverter
    import java.security.Key

    //The JWT signature algorithm we will be using to sign the token//The JWT signature algorithm we will be using to sign the token
    val signatureAlgorithm = SignatureAlgorithm.HS256

    val nowMillis = System.currentTimeMillis
    val now = new Date(nowMillis)

    //We will sign our JWT with our ApiKey secret
    val apiKeySecretBytes = DatatypeConverter.parseBase64Binary(apiSecret)
    val signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName)

    //Let's set the JWT Claims
    val builder = Jwts.builder.setId(id).setIssuedAt(now).setSubject(subject).setIssuer(issuer).signWith(signatureAlgorithm, signingKey)

    //if it has been specified, let's add the expiration
    if (ttl >= 0) {
      val expMillis = nowMillis + ttl
      val exp = new Date(expMillis)
      builder.setExpiration(exp)
    }

    //Builds the JWT and serializes it to a compact, URL-safe string
    builder.compact
  }
}
