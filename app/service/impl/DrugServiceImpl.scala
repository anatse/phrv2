package service.impl

import com.google.inject.Inject
import model._
import play.api.cache.{NamedCache, SyncCacheApi}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Text
import reactivemongo.bson.{BSONArray, BSONDocumentReader, BSONDocumentWriter, BSONValue, Macros, document}
import service.DrugService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DrugServiceImpl @Inject() (val mongoApi: ReactiveMongoApi, @NamedCache("user-cache")cacheApi: SyncCacheApi, implicit val ex: ExecutionContext) extends MongoBaseDao with DrugService {
  private def productCollection:Future[BSONCollection] = mongoApi.database.map(_.collection("products"))
  private def recommendedCollection:Future[BSONCollection] = mongoApi.database.map(_.collection("selectprods"))

  private val regexp = "[ ,.\\+-]+"

  implicit def productWriter: BSONDocumentWriter[DrugsProduct] = Macros.writer[DrugsProduct]
  implicit def productReader: BSONDocumentReader[DrugsProduct] = Macros.reader[DrugsProduct]

  implicit def recProductWriter: BSONDocumentWriter[RecommendedDrugs] = Macros.writer[RecommendedDrugs]
  implicit def recProductReader: BSONDocumentReader[RecommendedDrugs] = Macros.reader[RecommendedDrugs]

  /**
    * Function create or replace text index for products
    * @return
    */
  override def createTextIndex () = productCollection.flatMap(
    collection => collection.indexesManager.create(Index(
      key = Seq(
        "drugsFullName" -> Text,
        "drugsShortName" -> Text,
        "drugFullName" -> Text,
        "producerFullName" -> Text,
        "producerShortName" -> Text,
        "supplierName" -> Text,
        "barCode" -> Text,
        "drugGroups" -> Text,
        "MNN" -> Text
      ),
      name = Some("productSearchText"),
      options = document (
        "default_language" -> "russian"
      )
    ))
  )

  /**
    * Function used to create soundex representation for the given word
    * @param inpStr input string
    * @return soundex representation
    */
  private def soundex (inpStr: String): String = cacheApi.getOrElseUpdate[String](s"soundex.$inpStr"){
    List[(String, String)](
      "ЙО|ИО|ЙЕ|ИЕ" -> "И",
      "О|Ы|Я" -> "А",
      "Е|Ё|Э" -> "И",
      "Ю" -> "У",
      "Б" -> "П",
      "З" -> "С",
      "Д" -> "Т",
      "В" -> "Ф",
      "Г" -> "К",
      "ТС|ДС" -> "Ц",
      "Н{2,}" -> "Н",
      "С{2,}" -> "С",
      "Р{2,}" -> "Р",
      "М{2,}" -> "М",
      "[УЕАЫОИЯЮЭ]{1,}$" -> ""
    ).foldLeft(inpStr.toUpperCase())((inp, m) => {inp.replaceAll(m._1, m._2)})
  }

  /**
    * Fills sondex words representation for given drug
    * @param drug
    * @return new drug product with soundex words
    */
  private def prepareDrug (drug: DrugsProduct): DrugsProduct = {
    val soundexWords = drug.drugsFullName.split(regexp).map(soundex(_)).foldLeft (drug.MNN.split(regexp).map(soundex(_))) (
      (arr, item) => {
        if (item == "" || arr.contains(item)) arr else arr :+ item
      }
    )

    drug.copy(sndWords = Some(soundexWords))
  }

  /**
    * Function fix keyboar layout for russian
    * @param str string to fix layout
    * @return
    */
  private def fixKeyboardLayout (str: String): String = cacheApi.getOrElseUpdate[String](s"ruslayout.$str"){
    val replacer = Map(
      'q' -> 'й', 'w' -> 'ц', 'e' -> 'у', 'r' -> 'к', 't' -> 'е', 'y' -> 'н', 'u' -> 'г',
      'i' -> 'ш', 'o' -> 'щ', 'p' -> 'з', '[' -> 'х', ']' -> 'ъ', 'a' -> 'ф', 's' -> 'ы',
      'd' -> 'в', 'f' -> 'а', 'g' -> 'п', 'h' -> 'р', 'j' -> 'о', 'k' -> 'л', 'l' -> 'д',
      ';' -> 'ж', '\'' -> 'э', 'z' -> 'я', 'x' -> 'ч', 'c' -> 'с', 'v' -> 'м', 'b' -> 'и',
      'n' -> 'т', 'm' -> 'ь', ',' -> 'б', '.' -> 'ю', '/' -> '.'
    )

    str.toLowerCase.map(c => replacer.getOrElse(c, c))
  }

  /**
    * Constant defines product projection, i.e. fields which will be passed to result object
    */
  private lazy val projection = document (
    "barCode" -> 1,
    "id" -> 1,
    "drugsID" -> 1,
    "drugsFullName" -> 1,
    "drugsShortName" -> 1,
    "ost" -> 1,
    "retailPrice" -> 1,
    "MNN" -> 1,
    "tradeTech" -> 1,
    "producerFullName" -> 1,
    "producerShortName" -> 1,
    "supplierFullName" -> 1,
    "unitFullName" -> 1,
    "unitShortName" -> 1,
    "packaging" -> 1,
    "drugGroups" -> 1,
    "shortName" -> 1,
    "drugImage" -> 1,
    "seoTags" -> 1
  )

  override def findById(id: String) = productCollection.flatMap(_.find(document("_id" -> id)).projection(projection).one[DrugsProduct])
  override def save(product: DrugsProduct) = productCollection.flatMap(_.update(document("_id" -> product.id), product, upsert = true).map(_.upserted.map(ups => product).head))
  override def remove(id: String) = productCollection.flatMap(_.remove(document("_id" -> id)).map(r => {}))

  private def buildSorts (fields:Option[Array[String]]) = fields.getOrElse(Array[String]("retailPrice")).map(f => document(f.split(":") match {
    case Array(s1, s2) => (s1 -> s2.toInt)
    case Array(s1) => (s1 -> 1)
  })).toSeq

  private def buildQueryArray (filter:DrugsFindRq, doc:BSONValue, onlyExistence:Boolean = false) = {
    var arr = BSONArray(doc)
    // Add groups information
    filter.groups match {
      case Some(groups) => arr = arr ++ document("drugGroups" -> document("$in" -> groups))
      case _ =>
    }

    // Add information about image (exists or not)
    if (filter.hasImage == 0) {
      arr = arr ++ document("drugImage" -> document("$exists" -> false))
    } else if (filter.hasImage == 1) {
      arr = arr ++ document("drugImage" -> document("$exists" -> true))
    }

    // Additional filter for quantity of products
    if (onlyExistence) {
      arr = arr ++ document("ost" -> document("$gt" -> 0))
    }

    arr
  }

  private def processWithAdditionalFilter (filter:DrugsFindRq)(doc:BSONValue) = productCollection.flatMap(_.find(
    document("$and" ->
      buildQueryArray (filter, doc),
    )).projection(projection).options(QueryOpts().skip(filter.offset).batchSize(filter.pageSize))
    .sort(document(buildSorts(filter.sorts):_*))
    .cursor[DrugsProduct]()
    .collect[List](filter.pageSize, handler[DrugsProduct]))

  /**
    * Function find drugs by drugsFullName only but user regexp search
    * @param filter data to filter request
    * @return list of the found drugs
    */
  private def searchbyDrugName (filter:DrugsFindRq) = processWithAdditionalFilter (filter) {
    filter.text match {
      case Some(text) => document("$or" -> BSONArray(
        document("drugsFullName" -> document("$regex" -> s".*${text}.*", "$options" -> "i")),
        document("drugsFullName" -> document("$regex" -> s".*${fixKeyboardLayout(text)}.*", "$options" -> "i"))
      ))
      case _ => document()
    }
  }

  /**
    * Function searches drugs using mongo text index. This text index custructs from name and description and producer of drugs
    * @param filter
    * @return list of found drugs
    */
  private def textSearch (filter:DrugsFindRq) = processWithAdditionalFilter (filter) {
    filter.text match {
      case Some(text) => document("$text" -> document(
        "$search" -> text,
        "$caseSensitive" -> false)
      )

      case _ => document()
    }
  }

  /**
    * Function searchs drugs using soundex function. This function prepares array of words and stored its within drugs object.
    * Eache text string will be splitted by words and next these words converts to sondex form. After mongo searchs intersects between
    * thwo words arrays - stored in database and computed from search string
    * @param filter
    * @return list of found drugs
    */
  private def fuzzySearch(filter:DrugsFindRq) = processWithAdditionalFilter (filter) {
    filter.text match {
      case Some(text) =>
        val words = text.split(regexp).map(soundex _)
        // Add fixed layout strings to find
        val allWords = words ++ fixKeyboardLayout(text).split(regexp).map(soundex _)
        document("sndWords" -> document("$in" -> allWords))

      case _ => document()
    }
  }

  override def getAll(dp:DrugsAdminRq) = productCollection.flatMap(
    _.find(
      if (dp.drugsFullName != "") {
        document("$or" -> BSONArray (
          document("drugsFullName" -> document("$regex" -> s".*${dp.drugsFullName}.*", "$options" -> "i")),
          document("drugsFullName" -> document("$regex" -> s".*${fixKeyboardLayout(dp.drugsFullName)}.*", "$options" -> "i"))
        ))
      }
      else
        document()
    )
      .projection(projection)
      .cursor[DrugsProduct]()
      .collect[List](-1, handler[DrugsProduct]))

  override def findRecommended= recommendedCollection.flatMap (
    _.find(document())
      .sort (document("orderNum" -> 1))
      .cursor[RecommendedDrugs]()
      .collect[List](-1, handler[RecommendedDrugs])
  ).flatMap(rd => {
    val drugIds = rd.map(r => r.drugProductId)
    val drugOrderMap = rd.map (r => (r.drugProductId -> r.orderNum)).toMap
    productCollection.flatMap(_.find(document("_id" -> document("$in" -> drugIds))).projection(projection)
      .cursor[DrugsProduct]()
      .collect[List](-1, handler[DrugsProduct])).map (list => list.sortWith((e1, e2) => drugOrderMap(e1.id) < drugOrderMap(e2.id)))
  })

  override def addRecommended (drugId: String, orderNum: Int): Future[Unit] = recommendedCollection.flatMap(
    _.update(document ("_id" -> drugId), RecommendedDrugs(drugId, orderNum), upsert = true)
  ).map(r => {})

  override def removeRecommended (drugId: String) = recommendedCollection.flatMap(_.remove(document("_id" -> drugId))).map(r => {})

  /**
    * Function updates or inserts given products. Only part of the drug attributes wil be changes. Suck additional attribues as
    * groups, seo tags and so on not changes by bulk upserts. Its can be changed manually only
    * @param entities
    * @return list of status of the operatiions
    */
  override def bulkUpsert (entities: List[DrugsProduct]): Future[Seq[UpdateWriteResult]] = Future.sequence (
    entities.map(prepareDrug(_)).map (entity => {
      val res = productCollection.flatMap(_.update(
        document(
          "_id" -> entity.id),
        document (
          "$set" -> document (
            "drugsID" -> entity.drugsID,
            "barCode" -> entity.barCode,
            "drugsFullName" -> entity.drugsFullName,
            "drugsShortName" -> entity.drugsShortName,
            "ost" -> entity.ost,
            "retailPrice" -> entity.retailPrice,
            "tradeTech" -> entity.tradeTech,
            "producerFullName" -> entity.producerFullName,
            "producerShortName" -> entity.producerShortName,
            "supplierFullName" -> entity.supplierFullName,
            "MNN" -> entity.MNN,
            "unitFullName" -> entity.unitFullName,
            "unitShortName" -> entity.unitShortName,
            "packaging" -> entity.packaging,
            "sndWords" -> entity.sndWords
          )
        ),
        upsert = true
      )
      )
      res.onComplete {
        case Failure(e) => e.printStackTrace()
        case Success(_) => {}
      }

      res
    }
    )
  )

  override def bulkInsert(entities: List[DrugsProduct]): Future[Unit] = productCollection.flatMap(
    col => {
      val bulkDocs = entities.map(implicitly[col.ImplicitlyDocumentProducer](_))
      col.insert[DrugsProduct](ordered = true).many(entities)
    }
  ).map(_ => {})

  /**
    * Function combines all types of search drugs in the following order <br/>
    * Next search algorithm will be used only if the previous search found no results
    * <ul>
    *   <li>Find by name</li>
    *   <li>Text search</li>
    *   <li>Fuzzy search</li>
    * </ul>
    *
    * @param filter
    * @return list of found drugs
    */
  override def combinedSearch (filter:DrugsFindRq) = searchbyDrugName(filter).flatMap(
    res => // If not found then trying to text search
      if (res.size == 0) {
        textSearch(filter).flatMap(result => // If not found then trying to fuzzy search
          if (result.size == 0)
            fuzzySearch(filter)
          else
            Future.successful(result)
        )
      }
      else Future.successful(res)
  )

  /**
    * Function set image to desired drug
    * @param id identifier of drug
    * @param imageUrl image url
    * @return changed drug
    */
  override def addImage(id: String, imageUrl: String) = productCollection.flatMap(_.findAndUpdate(
    document("drugsID" -> id),
    document("$set" -> document ( "drugImage" -> imageUrl)),
    fetchNewObject = true
  ).map(r => r.result[DrugsProduct]))

  /**
    * Function set image to desired drug
    * @param id identifier of drug
    * @param groups group list
    * @return changed drug
    */
  override def setGroups (id: String, groups: Array[String]) = productCollection.flatMap(_.findAndUpdate(
    document("_id" -> id),
    document("$set" -> document ( "drugGroups" -> groups)),
    fetchNewObject = true
  ).map(r => r.result[DrugsProduct]))

  /**
    * Function retrieves recommended drugs from database using sorting and paging
    * @param sortField sort
    * @param offset offset
    * @param pageSize page size
    * @return list of found drugs
    */
  override def findAll(sortField: Option[String], offset: Int, pageSize: Int) = findRecommended
}
