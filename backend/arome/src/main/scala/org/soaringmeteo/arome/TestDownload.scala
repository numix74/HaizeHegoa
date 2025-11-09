package org.soaringmeteo.arome

import org.soaringmeteo.arome.in.{AromeRun, AromeDownloader}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Test utility to verify AROME data download functionality
  *
  * Usage:
  *   sbt "arome/runMain org.soaringmeteo.arome.TestDownload"
  *   sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025 1 15 0"
  */
object TestDownload {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("=== AROME Download Test ===")

    // Parse command line arguments or use latest run
    val aromeRun = if (args.length >= 4) {
      val year = args(0).toInt
      val month = args(1).toInt
      val day = args(2).toInt
      val hour = args(3).toInt
      logger.info(s"Using specified run: $year-$month-$day $hour:00 UTC")
      AromeRun(year, month, day, hour)
    } else {
      logger.info("No date specified, finding latest run...")
      AromeRun.findLatest()
    }

    logger.info(s"AROME Run: $aromeRun")
    logger.info(s"Init DateTime: ${aromeRun.initDateTime}")
    logger.info(s"Date Directory: ${aromeRun.dateDirectory}")

    // Test URL availability
    if (args.contains("--check-urls")) {
      testUrlAvailability(aromeRun)
    }

    // Display sample URLs
    if (args.contains("--show-urls")) {
      displaySampleUrls(aromeRun)
    }

    // Test single file download
    if (args.contains("--download")) {
      testSingleDownload(aromeRun)
    }

    // If no flags provided, just show basic info
    if (!args.contains("--check-urls") && !args.contains("--show-urls") && !args.contains("--download")) {
      logger.info("\nUse flags to test different features:")
      logger.info("  --check-urls : Check if URLs are available")
      logger.info("  --show-urls  : Display sample URLs")
      logger.info("  --download   : Test downloading a single file")
      displaySampleUrls(aromeRun)
    }

    logger.info("\n=== Test Complete ===")
  }

  private def testUrlAvailability(aromeRun: AromeRun): Unit = {
    logger.info("\n--- Testing URL Availability ---")
    val packages = Seq("SP1", "SP2", "SP3")
    val hourOffsets = Seq(0, 3, 6)

    for {
      pkg <- packages
      hour <- hourOffsets
    } {
      val url = aromeRun.gribUrl(hour, pkg)
      val available = AromeDownloader.checkUrlAvailability(url)
      logger.info(f"  [$hour%2d] $pkg: ${if (available) "✓" else "✗"} $url")
    }
  }

  private def displaySampleUrls(aromeRun: AromeRun): Unit = {
    logger.info("\n--- Sample URLs ---")
    val packages = Seq("SP1", "SP2", "SP3")

    logger.info("Hour 0 (initial conditions):")
    packages.foreach { pkg =>
      logger.info(s"  $pkg: ${aromeRun.gribUrl(0, pkg)}")
    }

    logger.info("\nHour 6:")
    packages.foreach { pkg =>
      logger.info(s"  $pkg: ${aromeRun.gribUrl(6, pkg)}")
    }

    logger.info("\nHour 12:")
    packages.foreach { pkg =>
      logger.info(s"  $pkg: ${aromeRun.gribUrl(12, pkg)}")
    }
  }

  private def testSingleDownload(aromeRun: AromeRun): Unit = {
    logger.info("\n--- Testing Single File Download ---")
    val downloader = new AromeDownloader(downloadRateLimit = 60)
    val targetDir = os.temp.dir(prefix = "arome-test-")

    try {
      val targetFile = targetDir / "test.grib2"
      logger.info(s"Target file: $targetFile")
      logger.info(s"Attempting to download AROME SP1 hour 0...")

      // This will likely fail if the URL structure is not correct
      // or if authentication is required
      val future = downloader.scheduleDownload(
        targetFile = targetFile,
        aromeRun = aromeRun,
        hourOffset = 0,
        package_ = "SP1"
      )

      import scala.concurrent.Await
      import scala.concurrent.duration._

      Await.result(future, 5.minutes)

      if (os.exists(targetFile)) {
        val fileSize = os.size(targetFile)
        logger.info(s"✓ Download successful! File size: ${fileSize / 1024 / 1024} MB")
      } else {
        logger.error("✗ Download failed: file does not exist")
      }
    } catch {
      case e: Exception =>
        logger.error(s"✗ Download failed: ${e.getMessage}")
        logger.info("Note: AROME data might require authentication or the URL structure might be different")
    } finally {
      downloader.shutdown()
      os.remove.all(targetDir)
    }
  }
}
