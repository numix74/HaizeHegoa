package org.soaringmeteo.arome.in

import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import org.slf4j.LoggerFactory

/**
 * Metadata about a run of AROME HD 0.01°
 *
 * AROME runs hourly from 06Z to 21Z (16 runs per day)
 */
case class AromeRun(
  initDateTime: OffsetDateTime
) {

  private val logger = LoggerFactory.getLogger(getClass)

  // Format: "2025-11-09T12:00:00Z"
  private val initTimeString = initDateTime.toString

  // Run number mapping for hourly runs (06Z=006, 07Z=007, ..., 21Z=021)
  private val runNumber = f"${initDateTime.getHour}%03d"

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
   * @param packageName SP1, HP1, HP2, or HP3
   * @param hourOffset Hour offset from run initialization (0-24)
   * @return Filename like: arome__005__HP1__12H__2025-11-09T12:00:00Z.grib2
   */
  def fileName(packageName: String, hourOffset: Int): String = {
    val hourString = f"${hourOffset}%02dH"
    s"arome__${runNumber}__${packageName}__${hourString}__${initTimeString}.grib2"
  }

  /**
   * Full URL to download a GRIB2 file from data.gouv.fr
   *
   * URL pattern:
   * https://object.files.data.gouv.fr/meteofrance-pnt/pnt/{run_time}/arome/{run_number}/{package}/arome__{run_number}__{package}__{hour}H__{run_time}.grib2
   *
   * @param packageName SP1, HP1, HP2, or HP3
   * @param hourOffset Hour offset from run initialization (0-24)
   * @return Full URL to download
   */
  def gribUrl(packageName: String, hourOffset: Int): String = {
    val baseUrl = "https://object.files.data.gouv.fr/meteofrance-pnt/pnt"
    val hourString = f"${hourOffset}%02dH"
    val filename = s"arome__${runNumber}__${packageName}__${hourString}__${initTimeString}.grib2"

    s"$baseUrl/$initTimeString/arome/$runNumber/$packageName/$filename"
  }

  /**
   * Get all package URLs for a specific hour
   * @param hourOffset Hour offset from run initialization (0-24)
   * @return Map of package name -> URL
   */
  def allPackageUrls(hourOffset: Int): Map[String, String] = {
    Map(
      "SP1" -> gribUrl("SP1", hourOffset),
      "HP1" -> gribUrl("HP1", hourOffset),
      "HP2" -> gribUrl("HP2", hourOffset),
      "HP3" -> gribUrl("HP3", hourOffset)
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
