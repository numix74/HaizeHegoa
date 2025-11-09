package org.soaringmeteo.arome

import scala.jdk.CollectionConverters._

case class AromeSetting(
  name: String,
  zone: AromeZone,
  gribDirectory: String,
  outputDirectory: String
)

object Settings {

  // Default download rate limit: 60 requests per minute for data.gouv.fr
  val downloadRateLimit: Int = {
    val config = com.typesafe.config.ConfigFactory.load()
    if (config.hasPath("arome.download-rate-limit"))
      config.getInt("arome.download-rate-limit")
    else
      60
  }

  // Enable/disable automatic GRIB download
  val enableDownload: Boolean = {
    val config = com.typesafe.config.ConfigFactory.load()
    if (config.hasPath("arome.enable-download"))
      config.getBoolean("arome.enable-download")
    else
      false
  }

  // Specific AROME run time to use (00, 03, 06, 09, 12, 15, 18, or 21)
  // If None, will use the latest available run
  val aromeRunInitTime: Option[String] = {
    val config = com.typesafe.config.ConfigFactory.load()
    if (config.hasPath("arome.run-init-time"))
      Some(config.getString("arome.run-init-time"))
    else
      None
  }

  def fromCommandLineArguments(args: Array[String]): Settings = {
    if (args.length < 1) {
      throw new Exception("Usage: arome <config-file>")
    }
    val configFile = args(0)
    fromConfig(configFile)
  }

  def fromConfig(configPath: String): Settings = {
    val config = com.typesafe.config.ConfigFactory.parseFile(new java.io.File(configPath))

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
        gribDirectory = zoneConfig.getString("grib-directory"),
        outputDirectory = zoneConfig.getString("output-directory")
      )
    }.toList

    Settings(zones)
  }
}

case class Settings(zones: List[AromeSetting])
