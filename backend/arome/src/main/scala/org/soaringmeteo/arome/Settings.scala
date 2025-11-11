package org.soaringmeteo.arome

import scala.jdk.CollectionConverters._

case class AromeSetting(
  name: String,
  zone: AromeZone,
  gribDirectory: String
)

object Settings {
  def fromCommandLineArguments(args: Array[String]): Settings = {
    if (args.length < 1) {
      throw new Exception("Usage: arome <config-file>")
    }
    val configFile = args(0)
    fromConfig(configFile)
  }

  def fromConfig(configPath: String): Settings = {
    val config = com.typesafe.config.ConfigFactory.parseFile(new java.io.File(configPath))

    val outputBaseDirectory = config.getString("arome.output-base-directory")

    val zones = config.getConfigList("arome.zones").asScala.map { zoneConfig =>
      val name = zoneConfig.getString("name")
      val lonMin = zoneConfig.getDouble("lon-min")
      val lonMax = zoneConfig.getDouble("lon-max")
      val latMin = zoneConfig.getDouble("lat-min")
      val latMax = zoneConfig.getDouble("lat-max")
      val step = zoneConfig.getDouble("step")

      val zone = AromeZone(
        longitudes = BigDecimal(lonMin).to(BigDecimal(lonMax), BigDecimal(step)).map(_.toDouble).toIndexedSeq,
        latitudes = BigDecimal(latMax).to(BigDecimal(latMin), BigDecimal(-step)).map(_.toDouble).toIndexedSeq
      )

      AromeSetting(
        name = name,
        zone = zone,
        gribDirectory = zoneConfig.getString("grib-directory")
      )
    }.toList

    Settings(outputBaseDirectory, zones)
  }
}

case class Settings(outputBaseDirectory: String, zones: List[AromeSetting])
