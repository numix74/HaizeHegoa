package org.soaringmeteo.arome.out

import geotrellis.vector.Extent
import org.slf4j.LoggerFactory
import org.soaringmeteo.InitDateString
import org.soaringmeteo.Point
import org.soaringmeteo.arome.{AromeSetting, AromeVectorTilesParameters}
import org.soaringmeteo.out.{ForecastMetadata, JsonData, deleteOldData}

import java.time.OffsetDateTime
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Produce soaring forecast JSON data from AROME forecast data.
 *
 * Similar to GFS JsonWriter, but adapted for AROME zones and data structure.
 */
object JsonWriter {

  private val logger = LoggerFactory.getLogger(getClass)

  // AROME provides forecasts from H+0 to H+42 (or H+24 for simplified runs)
  private val forecastHours = (0 to 24).toSeq

  // Number of forecasts per day (for calculating relevant time steps)
  private val numberOfForecastsPerDay = 8

  // Time resolution in hours
  private val forecastTimeResolution = 3

  // Number of forecast periods to keep per day
  private val relevantForecastPeriodsPerDay = 2

  // Number of forecast runs to keep in history
  private val forecastHistory = 5

  /**
   * Extract data from AROME forecast in the form of JSON documents.
   *
   * @param versionedTargetDir Directory where we write our resulting JSON documents
   * @param initTime           Initialization time of the forecast run
   * @param settings           AROME settings with zones configuration
   */
  def writeJsons(
    versionedTargetDir: os.Path,
    initTime: OffsetDateTime,
    settings: org.soaringmeteo.arome.Settings
  ): Unit = {
    val initDateString = InitDateString(initTime)

    // Write all the data in a subdirectory named according to the
    // initialization time of the AROME run (e.g., `2025-01-08T12`)
    val targetRunDir = versionedTargetDir / initDateString
    logger.info(s"Writing location forecasts in $targetRunDir")

    for (setting <- settings.zones) {
      // Write the data corresponding to every zone in a specific subdirectory
      val zoneId = toZoneId(setting.name)
      val zoneTargetDir = targetRunDir / zoneId
      os.makeDir.all(zoneTargetDir)

      // Write detailed forecast over time for every point of the grid
      writeForecastsByLocation(initTime, setting, zoneId, zoneTargetDir)
    }

    // Update the file `forecast.json` in the root target directory
    val currentForecasts =
      overwriteLatestForecastMetadata(initDateString, initTime, versionedTargetDir, settings)

    // Remove expired forecast data from the target directory
    deleteOldData(versionedTargetDir, currentForecasts)
  }

  /**
   * Write one JSON file per cluster of points, containing the forecast data
   * for the next hours.
   */
  private def writeForecastsByLocation(
    initTime: OffsetDateTime,
    setting: AromeSetting,
    zoneId: String,
    targetDir: os.Path
  ): Unit = {
    val cache = mutable.Map.empty[BigDecimal, OffsetDateTime => Boolean]

    JsonData.writeForecastsByLocation(
      s"zone ${setting.name}",
      setting.zone.longitudes.size,
      setting.zone.latitudes.size,
      targetDir
    ) { (x, y) =>
      val location = Point(setting.zone.latitudes(y), setting.zone.longitudes(x))
      val isRelevant =
        cache.getOrElseUpdate(location.longitude, relevantTimeStepsForLocation(location))
      val relevantHours =
        forecastHours
          .view
          .filter { hourOffset => isRelevant(initTime.plusHours(hourOffset)) }
          .toSet
      Await.result(
        Store.forecastForLocation(initTime, zoneId, x, y, relevantHours),
        300.second
      )
    }
  }

  /**
   * Determine which time steps are relevant for a given location based on its longitude.
   * This reduces the amount of data sent to the frontend by only including
   * forecasts around local noon time.
   */
  private def relevantTimeStepsForLocation(location: Point): OffsetDateTime => Boolean = {
    // Transform longitude so that it goes from 0 to 360 instead of 180 to -180
    val normalizedLongitude = 180 - location.longitude
    // Width of each zone, in degrees
    val zoneWidthDegrees = 360 / numberOfForecastsPerDay
    // Width of each zone, in hours
    val zoneWidthHours = forecastTimeResolution

    // Calculate noon hour for this longitude
    val noonHour =
      ((normalizedLongitude + (zoneWidthDegrees / 2.0)) % 360).doubleValue.round.toInt / zoneWidthDegrees * zoneWidthHours

    val allHours = (0 until 24 by forecastTimeResolution).to(Set)

    val relevantHours: Set[Int] =
      (1 to relevantForecastPeriodsPerDay).foldLeft((allHours, Set.empty[Int])) {
        case ((hs, rhs), _) =>
          val rh = hs.minBy(h => math.min(noonHour + 24 - h, math.abs(h - noonHour)))
          (hs - rh, rhs + rh)
      }._2

    time => {
      relevantHours.contains(time.getHour)
    }
  }

  /**
   * Update file `forecast.json` to point to the latest forecast data.
   *
   * @param initDateString Init date prefix
   * @param initTime       Initialization time
   * @param targetDir      Target directory
   * @param settings       AROME settings
   */
  private def overwriteLatestForecastMetadata(
    initDateString: String,
    initTime: OffsetDateTime,
    targetDir: os.Path,
    settings: org.soaringmeteo.arome.Settings
  ): Seq[ForecastMetadata] = {
    val zones =
      for (setting <- settings.zones) yield {
        val zoneId = toZoneId(setting.name)
        val resolution = BigDecimal("0.025")  // AROME resolution: 2.5 km ≈ 0.025 degrees

        val minLon = setting.zone.longitudes.head
        val maxLon = setting.zone.longitudes.last
        val minLat = setting.zone.latitudes.last
        val maxLat = setting.zone.latitudes.head

        // Get vector tiles parameters
        val vectorTilesParams = AromeVectorTilesParameters(setting.zone)

        ForecastMetadata.Zone(
          zoneId,
          s"${setting.name} AROME 2.5km",
          ForecastMetadata.Raster(
            projection = "EPSG:4326" /* WGS84 */,
            resolution,
            Extent(
              minLon,
              minLat,
              maxLon,
              maxLat
            ).buffer((resolution / 2).doubleValue) // Add buffer because we draw rectangles, not points
          ),
          ForecastMetadata.VectorTiles(vectorTilesParams.extent, vectorTilesParams.tileSize)
        )
      }

    ForecastMetadata.overwriteLatestForecastMetadata(
      targetDir,
      forecastHistory,
      initDateString,
      initTime,
      maybeFirstTimeStep = None, // In AROME, the first time-step is the same as initialization time
      forecastHours.last,
      zones
    )
  }

  /**
   * Convert zone name to a valid zone ID (lowercase, hyphens instead of spaces)
   */
  private def toZoneId(name: String): String = {
    name.toLowerCase.replace(" ", "-")
  }

}
