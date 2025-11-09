package org.soaringmeteo.arome.in

import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import org.slf4j.LoggerFactory

/**
 * Metadata about a run of AROME France HD (1.3km)
 *
 * AROME runs hourly from 06Z to 21Z (16 runs per day)
 *
 * Data source: https://object.data.gouv.fr/meteofrance-pnt/pnt/
 * URL format: YYYYMMDD/arome/{HH}H/SP1_arome-france-hd_{HH}H_YYYYMMDDHH.grib2
 */
case class AromeRun(
  initDateTime: OffsetDateTime
) {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Storage path for downloaded GRIB files
   * @param base Base directory for AROME GRIB files
   * @return Path like: base/20251109/12Z/
   */
  def storagePath(base: os.Path): os.Path = {
    val dateString = f"${initDateTime.getYear}%04d${initDateTime.getMonthValue}%02d${initDateTime.getDayOfMonth}%02d"
    val hourString = f"${initDateTime.getHour}%02dZ"
    base / dateString / hourString
  }

  /**
   * Filename for a specific package and hour offset
   *
   * Format: {package}_arome-france-hd_{run}H_{YYYYMMDDHH}.grib2
   *
   * @param packageName SP1, SP2, SP3, or HP1
   * @param hourOffset Hour offset from run initialization (0-24) - currently unused, same file for all hours
   * @return Filename like: SP1_arome-france-hd_12H_2025110912.grib2
   */
  def fileName(packageName: String, hourOffset: Int = 0): String = {
    val dateTimeString = f"${initDateTime.getYear}%04d${initDateTime.getMonthValue}%02d${initDateTime.getDayOfMonth}%02d${initDateTime.getHour}%02d"
    val runHour = f"${initDateTime.getHour}%02dH"
    s"${packageName}_arome-france-hd_${runHour}_${dateTimeString}.grib2"
  }

  /**
   * Full URL to download a GRIB2 file from data.gouv.fr
   *
   * URL pattern:
   * https://object.data.gouv.fr/meteofrance-pnt/pnt/YYYYMMDD/arome/{run}H/{package}_arome-france-hd_{run}H_YYYYMMDDHH.grib2
   *
   * Example:
   * https://object.data.gouv.fr/meteofrance-pnt/pnt/20251015/arome/00H/SP1_arome-france-hd_00H_2025101500.grib2
   *
   * @param packageName SP1, SP2, SP3, or HP1
   * @param hourOffset Hour offset - currently unused as AROME provides single files per run
   * @return Full URL to download
   */
  def gribUrl(packageName: String, hourOffset: Int = 0): String = {
    val baseUrl = "https://object.data.gouv.fr/meteofrance-pnt/pnt"
    val dateString = f"${initDateTime.getYear}%04d${initDateTime.getMonthValue}%02d${initDateTime.getDayOfMonth}%02d"
    val runHour = f"${initDateTime.getHour}%02dH"
    val filename = fileName(packageName, hourOffset)

    s"$baseUrl/$dateString/arome/$runHour/$filename"
  }

  /**
   * Get all package URLs for a specific run
   *
   * Note: AROME provides single files per run (not per forecast hour)
   *
   * @return Map of package name -> URL
   */
  def allPackageUrls(): Map[String, String] = {
    Map(
      "SP1" -> gribUrl("SP1"),
      "SP2" -> gribUrl("SP2"),
      "SP3" -> gribUrl("SP3"),
      "HP1" -> gribUrl("HP1")
    )
  }

}

object AromeRun {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Find the latest available AROME run
   *
   * AROME runs hourly from 06Z to 21Z (16 runs per day)
   * Files are typically available 1-2 hours after run initialization
   *
   * @param maybeAromeRunInitTime Optional specific init time ("06", "07", ..., "21")
   * @return AromeRun metadata
   */
  def findLatest(maybeAromeRunInitTime: Option[String] = None): AromeRun = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    val initHour = maybeAromeRunInitTime match {
      case Some(timeString) =>
        val hour = timeString.toInt
        require(hour >= 6 && hour <= 21, s"Invalid AROME run time: $timeString. Must be between 06 and 21 (hourly)")
        hour

      case None =>
        // Find most recent hourly run between 06Z and 21Z, minus 2 hours to ensure data availability
        val hoursAgo = 2
        val effectiveTime = now.minusHours(hoursAgo)
        val effectiveHour = effectiveTime.getHour

        // Clamp to 06-21 range
        val latestRunHour = if (effectiveHour < 6) {
          21 // Use 21Z from previous day
        } else if (effectiveHour > 21) {
          21 // Use 21Z from today
        } else {
          effectiveHour // Use the hour itself (06-21)
        }
        latestRunHour
    }

    val runDate = maybeAromeRunInitTime match {
      case Some(_) => now.toLocalDate
      case None =>
        // If latest run hour is 21 and current time is before 23Z, use today
        // Otherwise, if we're before 06Z, use previous day's 21Z run
        val effectiveTime = now.minusHours(2)
        if (effectiveTime.getHour < 6) {
          now.toLocalDate.minusDays(1)
        } else {
          now.toLocalDate
        }
    }

    val initDateTime = OffsetDateTime.of(
      runDate,
      LocalTime.of(initHour, 0),
      ZoneOffset.UTC
    )

    logger.info(s"Using AROME run initialized at: $initDateTime")

    AromeRun(initDateTime)
  }

  /**
   * Create AromeRun for a specific date and time
   *
   * @param hour Must be between 6 and 21 (hourly runs)
   */
  def apply(year: Int, month: Int, day: Int, hour: Int): AromeRun = {
    require(hour >= 6 && hour <= 21, s"Invalid AROME run hour: $hour. Must be between 06 and 21 (hourly)")

    val initDateTime = OffsetDateTime.of(
      LocalDate.of(year, month, day),
      LocalTime.of(hour, 0),
      ZoneOffset.UTC
    )

    AromeRun(initDateTime)
  }

}
