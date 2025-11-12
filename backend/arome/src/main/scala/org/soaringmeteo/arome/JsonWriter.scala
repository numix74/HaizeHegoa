package org.soaringmeteo.arome

import geotrellis.vector.Extent
import org.slf4j.LoggerFactory
import org.soaringmeteo.out.{ForecastMetadata, deleteOldData}
import org.soaringmeteo.InitDateString
import java.time.{OffsetDateTime, Period}

/**
 * Produce soaring forecast data from AROME forecast data.
 */
object JsonWriter {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Extract data from the AROME forecast in the form of JSON documents.
   *
   * @param versionedTargetDir Directory where we write our resulting JSON documents
   * @param initDateTime       Initialization time of the AROME run
   * @param settings           AROME settings containing zones configuration
   * @param forecastHours      List of forecast hour offsets to process
   */
  def writeJsons(
    versionedTargetDir: os.Path,
    initDateTime: OffsetDateTime,
    settings: Settings,
    forecastHours: Seq[Int]
  ): Unit = {
    val initDateString = InitDateString(initDateTime)
    logger.info(s"Writing forecast metadata for AROME run $initDateString")

    // TODO: Implement detailed location forecasts generation
    // For now, we only generate the forecast.json metadata file
    // which is sufficient for the frontend to display maps and vector tiles

    // Update the file `forecast.json` in the root target directory
    val currentForecasts =
      overwriteLatestForecastMetadata(initDateString, initDateTime, versionedTargetDir, settings, forecastHours)

    // Finally, we remove the expired forecast data from the target directory
    deleteOldData(versionedTargetDir, currentForecasts)
  }

  /**
   * Update file `forecast.json` to point to the latest forecast data.
   *
   * @param initDateString Init date prefix
   * @param initDateTime   AROME run initialization time
   * @param targetDir      Target directory
   * @param settings       AROME settings
   * @param forecastHours  List of forecast hour offsets
   */
  private def overwriteLatestForecastMetadata(
    initDateString: String,
    initDateTime: OffsetDateTime,
    targetDir: os.Path,
    settings: Settings,
    forecastHours: Seq[Int]
  ): Seq[ForecastMetadata] = {
    val zones =
      for (zoneSetting <- settings.zones) yield {
        val zone = zoneSetting.zone
        val resolution = BigDecimal("0.025") // AROME 2.5 km resolution

        val minLon = zone.longitudes.head
        val maxLon = zone.longitudes.last
        val minLat = zone.latitudes.last
        val maxLat = zone.latitudes.head

        ForecastMetadata.Zone(
          zoneSetting.name,
          zoneSetting.name.capitalize, // Use zone name as label
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
          ForecastMetadata.VectorTiles(
            AromeVectorTilesParameters(zone),
            512 // Default tile size
          )
        )
      }

    ForecastMetadata.overwriteLatestForecastMetadata(
      targetDir,
      Period.ofDays(2), // Keep 2 days of history
      initDateString,
      initDateTime,
      maybeFirstTimeStep = None, // In AROME, the first time-step is always the same as the initialization time
      forecastHours.last,
      zones
    )
  }

}
