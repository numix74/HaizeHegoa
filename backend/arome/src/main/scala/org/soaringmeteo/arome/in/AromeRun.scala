package org.soaringmeteo.arome.in

import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import org.slf4j.LoggerFactory

/** Metadata about a run of AROME model
  *
  * @param year  Year of the run (e.g., 2025)
  * @param month Month of the run (1-12)
  * @param day   Day of the run (1-31)
  * @param hour  Hour of the run (0, 3, 6, 9, 12, 15, 18, 21)
  */
case class AromeRun(
  year: Int,
  month: Int,
  day: Int,
  hour: Int
) {

  require(hour % 3 == 0 && hour >= 0 && hour < 24, s"Invalid hour: $hour. Must be 0, 3, 6, 9, 12, 15, 18, or 21")
  require(month >= 1 && month <= 12, s"Invalid month: $month")
  require(day >= 1 && day <= 31, s"Invalid day: $day")

  val initDateTime: OffsetDateTime = OffsetDateTime.of(
    LocalDate.of(year, month, day),
    LocalTime.of(hour, 0),
    ZoneOffset.UTC
  )

  /** Directory name for this run (e.g., "2025/01/15/00") */
  def dateDirectory: String = f"$year%04d/$month%02d/$day%02d/$hour%02d"

  /** Short date string (e.g., "20250115") */
  def dateString: String = f"$year%04d$month%02d$day%02d"

  /** Hour string (e.g., "00") */
  def hourString: String = f"$hour%02d"

  /**
    * Base URL for AROME data on Météo-France servers
    * Note: This is a placeholder. Actual URL structure depends on the data source.
    * Common sources:
    * - AROME France: https://donneespubliques.meteofrance.fr/
    * - European Weather Cloud: https://ewc.ecmwf.int/
    */
  def baseUrl(dataSource: String = "meteofrance"): String = dataSource match {
    case "meteofrance" =>
      s"https://donneespubliques.meteofrance.fr/?fond=produit&id_produit=131&id_rubrique=51"
    case _ =>
      throw new IllegalArgumentException(s"Unknown data source: $dataSource")
  }

  /**
    * Generate URL for a specific AROME GRIB file
    *
    * @param hourOffset Number of hours since initialization time (0-42 for AROME France)
    * @param package_   Package name (e.g., "SP1", "SP2", "SP3", "HP1", "HP2", "HP3")
    * @return URL to download the GRIB file
    */
  def gribUrl(hourOffset: Int, package_ : String): String = {
    // This is a placeholder implementation
    // The actual URL structure depends on the specific AROME data source and access method
    // For example, Météo-France provides data through their API or FTP server
    s"${baseUrl()}/AROME/${dateDirectory}/AROME_${dateString}_${hourString}_${package_}_$hourOffset.grib2"
  }

  /**
    * Path to store downloaded GRIB files
    *
    * @param base Base directory for storage
    */
  def storagePath(base: os.Path): os.Path =
    base / dateString / hourString

  override def toString: String = s"AromeRun($year-$month-$day ${hour}:00 UTC)"
}

object AromeRun {

  private val logger = LoggerFactory.getLogger(classOf[AromeRun])

  /**
    * Find the latest available AROME run
    *
    * This is a simplified implementation. In practice, you would need to:
    * 1. Query the Météo-France API or scrape their website
    * 2. Check which runs are available
    * 3. Return the most recent one
    *
    * For now, this returns the most recent run based on current time,
    * assuming a 3-hour delay for data availability.
    */
  def findLatest(): AromeRun = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    // AROME runs are typically available with a 3-hour delay
    val availableTime = now.minusHours(3)

    // Round down to the nearest 3-hour interval
    val hour = (availableTime.getHour / 3) * 3
    val date = if (hour <= availableTime.getHour) {
      availableTime.toLocalDate
    } else {
      availableTime.toLocalDate.minusDays(1)
    }

    val run = AromeRun(
      year = date.getYear,
      month = date.getMonthValue,
      day = date.getDayOfMonth,
      hour = hour
    )

    logger.info(s"Found latest AROME run: $run")
    run
  }

  /**
    * Create an AromeRun from a date string
    *
    * @param dateStr Date string in format "YYYYMMDD"
    * @param hour    Hour (0, 3, 6, 9, 12, 15, 18, 21)
    */
  def fromDateString(dateStr: String, hour: Int): AromeRun = {
    require(dateStr.length == 8, s"Invalid date string: $dateStr. Expected format: YYYYMMDD")
    val year = dateStr.substring(0, 4).toInt
    val month = dateStr.substring(4, 6).toInt
    val day = dateStr.substring(6, 8).toInt
    AromeRun(year, month, day, hour)
  }
}
