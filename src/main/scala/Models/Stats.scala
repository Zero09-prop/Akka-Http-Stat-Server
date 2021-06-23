package Models

import scala.collection.mutable

final case class Stats(node: Int, colSites: mutable.Map[String, Long])
