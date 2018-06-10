package model

import reactivemongo.bson.Macros.Annotations.{Ignore, Key}

/**
  * Class stores information about product. Uses to transfer data from "m-apteka plus" database
  * @param id
  * @param barCode
  * @param drugsFullName
  * @param drugsShortName
  * @param ost
  * @param retailPrice
  * @param tradeTech
  * @param producerFullName
  * @param producerShortName
  * @param supplierFullName
  * @param MNN
  * @param unitFullName
  * @param unitShortName
  * @param packaging
  */
case class DrugsProduct(
  @Key ("_id") id: String,
  barCode: String,

  drugsID: String,
  drugsFullName: String,
  drugsShortName: String,

  ost: Double,
  retailPrice: Double,

  tradeTech: String,
  producerFullName: String,
  producerShortName: String,
  supplierFullName: String,
  MNN: String,

  unitFullName: String,
  unitShortName: String,

  packaging: String,
  sndWords: Option[Array[String]] = None,

  // Additional attributes for manual adding
  drugGroups: Option[Array[String]] = None,
  drugImage: Option[String] = None,
  shortName: Option[String] = None,
  seoTags: Option[Array[String]] = None
)

/**
  * class represents drugs group
  * @param id
  * @param groupName
  * @param description
  */
case class DrugsGroup (id: String, groupName: String, description: String)

/**
  * Class represents recopmended drugs
  * @param drugProductId
  * @param orderNum
  */
case class RecommendedDrugs (@Key ("_id") drugProductId: String, orderNum: Int)
