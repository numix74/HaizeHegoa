package org.soaringmeteo.arome.in

import org.slf4j.LoggerFactory
import org.soaringmeteo.util.{RateLimiter, daemonicThreadFactory}
import squants.time.RevolutionsPerMinute

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/**
 * Utility to download AROME France HD (1.3km) GRIB2 files from data.gouv.fr
 *
 * Data source: https://object.data.gouv.fr/meteofrance-pnt/pnt/
 * URL format: YYYYMMDD/arome/{HH}H/SP1_arome-france-hd_{HH}H_YYYYMMDDHH.grib2
 *
 * Packages available:
 * - SP1: Surface parameters (temperature, wind at 10m, etc.)
 * - SP2: Boundary layer (PBLH, CAPE, clouds, terrain)
 * - SP3: Radiative fluxes (sensible/latent heat, solar radiation)
 * - HP1: Multi-level winds (20m to 3000m AGL)
 *
 * Note: Each GRIB file contains all forecast hours (0-24H) for a given run
 *
 * Downloads are rate-limited to avoid overwhelming the server.
 */
class AromeDownloader(downloadRateLimit: Int = 60) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val rateLimiter = new RateLimiter(RevolutionsPerMinute(downloadRateLimit))

  // Use fewer threads than GFS since AROME files are larger and data.gouv.fr may have stricter limits
  private val severalThreads = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(2, daemonicThreadFactory)
  )

  /**
   * Schedule download of a single AROME GRIB2 file
   *
   * @param targetFile Local path where to save the file
   * @param aromeRun Run metadata (initialization time)
   * @param packageName Package to download (SP1, SP2, SP3, or HP1)
   * @return Future containing the path to the downloaded file
   */
  def scheduleDownload(
    targetFile: os.Path,
    aromeRun: AromeRun,
    packageName: String
  ): Future[os.Path] =
    rateLimiter.submit(severalThreads) {
      logger.debug(s"Downloading AROME $packageName for run ${aromeRun.initDateTime}")
      download(targetFile, aromeRun, packageName)
      targetFile
    }

  /**
   * Download a single AROME GRIB2 file with retry logic
   */
  private def download(
    targetFile: os.Path,
    aromeRun: AromeRun,
    packageName: String
  ): Unit = concurrent.blocking {
    val url = aromeRun.gribUrl(packageName)

    // AROME files are typically available 1-2 hours after run initialization
    // We allow more retries than GFS since the availability window may be tighter
    val response = insist(maxAttempts = 12, delay = 2.minutes, url)

    os.write(targetFile, response.data.array)
    logger.debug(s"Downloaded $targetFile (${response.data.array.length / 1024 / 1024} MB)")
  }

  /**
   * Download all packages for a specific run
   *
   * @param baseDir Base directory where to save files
   * @param aromeRun Run metadata
   * @return Future containing a map of package name -> local file path
   */
  def scheduleDownloadAllPackages(
    baseDir: os.Path,
    aromeRun: AromeRun
  ): Future[Map[String, os.Path]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val packageNames = Seq("SP1", "SP2", "SP3", "HP1")
    val downloadFutures = packageNames.map { packageName =>
      val targetFile = baseDir / aromeRun.fileName(packageName)
      os.makeDir.all(targetFile / os.up)

      scheduleDownload(targetFile, aromeRun, packageName)
        .map(path => packageName -> path)
    }

    Future.sequence(downloadFutures).map(_.toMap)
  }

  /**
   * Try to fetch `url` at most `maxAttempts` times, waiting `delay` between each attempt.
   *
   * This is necessary because AROME files may not be immediately available after run initialization.
   */
  def insist(maxAttempts: Int, delay: Duration, url: String): requests.Response = {
    val errorOrSuccessfulResponse =
      Try(requests.get(url, readTimeout = 1200000, check = false)) match {
        case Failure(exception) => Left(exception)
        case Success(response) =>
          if (response.statusCode == 200) {
            Right(response)
          } else {
            Left(new Exception(s"HTTP ${response.statusCode}: ${response.statusMessage}"))
          }
      }

    errorOrSuccessfulResponse match {
      case Right(response) => response
      case Left(error) =>
        if (maxAttempts <= 1 || !NonFatal(error)) {
          logger.error(s"Failed to fetch $url: $error")
          throw new RuntimeException(s"Unable to fetch $url after all retries.")
        } else {
          val remainingAttempts = maxAttempts - 1
          logger.info(s"Failed to fetch $url: $error. Waiting ${delay.toSeconds}s... ($remainingAttempts attempts left)")
          Thread.sleep(delay.toMillis)
          insist(remainingAttempts, delay, url)
        }
    }
  }

}

object AromeDownloader {

  /**
   * Verify that a URL is accessible (useful for testing)
   */
  def checkUrlAvailability(url: String): Boolean = {
    Try(requests.head(url, readTimeout = 30000, check = false)) match {
      case Success(response) => response.statusCode == 200
      case Failure(_) => false
    }
  }

  /**
   * Test if a specific AROME run is available for download
   */
  def testRunAvailability(aromeRun: AromeRun): Map[String, Boolean] = {
    val packages = Seq("SP1", "SP2", "SP3", "HP1")
    packages.map { pkg =>
      val url = aromeRun.gribUrl(pkg)
      pkg -> checkUrlAvailability(url)
    }.toMap
  }

}
