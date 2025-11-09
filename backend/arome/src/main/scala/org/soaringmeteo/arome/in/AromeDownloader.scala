package org.soaringmeteo.arome.in

import org.slf4j.LoggerFactory
import squants.time.RevolutionsPerMinute

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/**
  * Utility to download AROME GRIB files from Météo-France servers.
  *
  * Similar to GribDownloader but adapted for AROME data sources.
  *
  * @param downloadRateLimit Maximum number of requests per minute to avoid rate limiting
  * @param maxParallelDownloads Maximum number of parallel downloads
  */
class AromeDownloader(
  downloadRateLimit: Int = 60,
  maxParallelDownloads: Int = 3
) {

  private val logger = LoggerFactory.getLogger(getClass)

  // Note: We're not using RateLimiter here yet to avoid dependencies
  // If needed, you can add it similar to GribDownloader

  // We use a dedicated thread pool with a fixed number of threads to avoid trying to download
  // all files at the same time.
  private val severalThreads = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(
      maxParallelDownloads,
      org.soaringmeteo.util.daemonicThreadFactory
    )
  )

  /**
    * Schedule a download of an AROME GRIB file
    *
    * @param targetFile Path where the file should be saved
    * @param aromeRun   Metadata about the AROME run
    * @param hourOffset Forecast hour offset (0-42)
    * @param package_   Package name (e.g., "SP1", "SP2", "SP3")
    * @return Future that completes when the download is done
    */
  def scheduleDownload(
    targetFile: os.Path,
    aromeRun: AromeRun,
    hourOffset: Int,
    package_ : String
  )(implicit ec: ExecutionContext): Future[os.Path] = {
    Future {
      logger.debug(s"Downloading AROME $package_ data at hour $hourOffset for run $aromeRun")
      download(targetFile, aromeRun, hourOffset, package_)
      targetFile
    }(severalThreads)
  }

  /**
    * Download an AROME GRIB file
    *
    * @param targetFile Path where the file should be saved
    * @param aromeRun   Metadata about the AROME run
    * @param hourOffset Forecast hour offset
    * @param package_   Package name
    */
  private def download(
    targetFile: os.Path,
    aromeRun: AromeRun,
    hourOffset: Int,
    package_ : String
  ): Unit = concurrent.blocking {
    val url = aromeRun.gribUrl(hourOffset, package_)
    logger.info(s"Downloading from: $url")

    // Create parent directory if it doesn't exist
    os.makeDir.all(targetFile / os.up)

    // Download with retry logic
    val response = insist(maxAttempts = 5, delay = 2.minutes, url)
    os.write(targetFile, response.data.array)
    logger.info(s"Downloaded $targetFile")
  }

  /**
    * Try to fetch a URL with retry logic
    *
    * @param maxAttempts Maximum number of attempts
    * @param delay       Delay between attempts
    * @param url         URL to fetch
    * @return Response if successful
    */
  def insist(maxAttempts: Int, delay: Duration, url: String): requests.Response = {
    val errorOrSuccessfulResponse =
      Try(requests.get(url, readTimeout = 1200000, check = false)) match {
        case Failure(exception) => Left(exception)
        case Success(response) =>
          if (response.statusCode == 200) Right(response)
          else Left(new Exception(s"Unexpected status code: ${response.statusCode} (${response.statusMessage})"))
      }

    errorOrSuccessfulResponse match {
      case Right(response) => response
      case Left(error) =>
        if (maxAttempts <= 1 || !NonFatal(error)) {
          logger.error(s"Failed to fetch $url: $error")
          throw new RuntimeException(s"Unable to fetch $url.", error)
        } else {
          val remainingAttempts = maxAttempts - 1
          logger.info(s"Failed to fetch $url: $error. Waiting ${delay.toSeconds} seconds... ($remainingAttempts remaining attempts)")
          Thread.sleep(delay.toMillis)
          insist(remainingAttempts, delay, url)
        }
    }
  }

  /**
    * Download multiple AROME GRIB files in parallel
    *
    * @param downloads Sequence of (targetFile, aromeRun, hourOffset, package) tuples
    * @return Future that completes when all downloads are done
    */
  def downloadBatch(
    downloads: Seq[(os.Path, AromeRun, Int, String)]
  )(implicit ec: ExecutionContext): Future[Seq[os.Path]] = {
    val futures = downloads.map { case (targetFile, aromeRun, hourOffset, package_) =>
      scheduleDownload(targetFile, aromeRun, hourOffset, package_)
    }
    Future.sequence(futures)
  }

  /**
    * Cleanup resources
    */
  def shutdown(): Unit = {
    severalThreads.shutdown()
  }
}

object AromeDownloader {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * Check if a URL is available (returns 200)
    *
    * @param url URL to check
    * @return true if the URL returns status 200
    */
  def checkUrlAvailability(url: String): Boolean = {
    Try {
      val response = requests.head(url, readTimeout = 30000, check = false)
      response.statusCode == 200
    } match {
      case Success(available) =>
        logger.debug(s"URL $url availability: $available")
        available
      case Failure(exception) =>
        logger.warn(s"Failed to check URL $url: ${exception.getMessage}")
        false
    }
  }

  /**
    * Get the size of a remote file
    *
    * @param url URL of the file
    * @return Size in bytes, or None if unavailable
    */
  def getFileSize(url: String): Option[Long] = {
    Try {
      val response = requests.head(url, readTimeout = 30000, check = false)
      response.headers.get("content-length").flatMap(_.headOption).map(_.toLong)
    } match {
      case Success(size) => size
      case Failure(exception) =>
        logger.warn(s"Failed to get file size for $url: ${exception.getMessage}")
        None
    }
  }
}
