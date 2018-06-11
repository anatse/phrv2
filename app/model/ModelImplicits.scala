package model

import play.api.libs.json.Json

object ModelImplicits {
  import play.api.mvc.Results._

  implicit val productWrites = Json.writes[DrugsProduct]
  implicit val productReads = Json.reads[DrugsProduct]

  implicit val producAdmRsWrites = Json.writes[DrugsAdminRq]
  implicit val producAdmRsReads = Json.reads[DrugsAdminRq]

  implicit val productRqWrites = Json.writes[DrugsFindRq]
  implicit val drugsFindRqReads = Json.reads[DrugsFindRq]

  implicit val groupWrites = Json.writes[DrugsGroup]
  implicit val groupReads = Json.reads[DrugsGroup]

  implicit val recProductReads = Json.reads[RecommendedDrugs]
  implicit val recProductWrites = Json.writes[RecommendedDrugs]

  implicit val readUpsertRes = Json.reads[UpsertRes]
  implicit val writeUpsertRes = Json.writes[UpsertRes]

  def makeResult (rows:List[DrugsProduct], realPageSize:Int, offset:Int) = {
    val filterredRows = if (rows.length > realPageSize) rows.dropRight(1) else rows
    Ok(Json.obj("rows" -> filterredRows, "pageSize" -> realPageSize, "offset" -> offset, "hasMore" -> (rows.length > realPageSize)))
  }
}
