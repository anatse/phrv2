package model

case class DrugsFindRq (
  groups: Option[Array[String]] = None,
  text: Option[String] = None,
  sorts: Option[Array[String]] = None,
  hasImage: Int,
  offset: Int,
  pageSize: Int
)

case class DrugsAdminRq (drugsFullName: String)
case class UpsertRes (ok:Int = 0, upserted: Int = 0, modified: Int = 0, errors: Int = 0)