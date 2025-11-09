package org.soaringmeteo.arome

import org.soaringmeteo.arome.in.{AromeRun, AromeDownloader}
import org.slf4j.LoggerFactory

import java.time.OffsetDateTime
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

/**
 * Test utility to verify AROME HD 0.01° download from data.gouv.fr
 *
 * Usage:
 *   sbt "arome/runMain org.soaringmeteo.arome.TestDownload"
 *   sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-09 12"
 */
object TestDownload {

  private val logger = LoggerFactory.getLogger(getClass)
  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    logger.info("=== AROME HD 0.01° Download Test ===")

    // Parse arguments or use latest run
    val aromeRun = if (args.length >= 2) {
      val Array(dateStr, hourStr) = args.take(2)
      val Array(year, month, day) = dateStr.split("-").map(_.toInt)
      val hour = hourStr.toInt
      AromeRun(year, month, day, hour)
    } else {
      logger.info("No arguments provided, using latest available run")
      AromeRun.findLatest()
    }

    logger.info(s"Testing AROME run: ${aromeRun.initDateTime}")

    // Test 1: Check URL availability
    logger.info("\n--- Test 1: Checking URL availability ---")
    testUrlAvailability(aromeRun)

    // Test 2: Display sample URLs
    logger.info("\n--- Test 2: Sample URLs ---")
    displaySampleUrls(aromeRun)

    // Test 3: Attempt to download a single file (hour 0, package SP1)
    if (args.contains("--download")) {
      logger.info("\n--- Test 3: Downloading SP1 package for hour 0 ---")
      testSingleDownload(aromeRun)
    } else {
      logger.info("\n--- Test 3: Skipped (use --download flag to test actual download) ---")
    }

    logger.info("\n=== Test Complete ===")
  }

  private def testUrlAvailability(aromeRun: AromeRun): Unit = {
    val packages = Seq("SP1", "SP2", "SP3", "HP1")

    logger.info(s"Checking availability for run ${aromeRun.initDateTime}...")

    packages.foreach { pkg =>
      val url = aromeRun.gribUrl(pkg)
      val available = AromeDownloader.checkUrlAvailability(url)

      val status = if (available) "✓ AVAILABLE" else "✗ NOT AVAILABLE"
      logger.info(f"  $pkg%-4s: $status")
      logger.debug(s"    URL: $url")
    }
  }

  private def displaySampleUrls(aromeRun: AromeRun): Unit = {
    logger.info("Sample URLs for all packages:")

    val packages = Seq("SP1", "SP2", "SP3", "HP1")

    packages.foreach { pkg =>
      val url = aromeRun.gribUrl(pkg)
      logger.info(s"  $pkg:")
      logger.info(s"    $url")
    }

    logger.info(s"\nStorage path: ${aromeRun.storagePath(os.pwd / "data" / "arome")}")
    logger.info(s"Filename example: ${aromeRun.fileName("SP1")}")
  }

  private def testSingleDownload(aromeRun: AromeRun): Unit = {
    val downloader = new AromeDownloader(downloadRateLimit = 60)
    val testDir = os.pwd / "test-download" / "arome"
    os.makeDir.all(testDir)

    val packageName = "SP1"
    val targetFile = testDir / aromeRun.fileName(packageName)

    logger.info(s"Downloading to: $targetFile")

    try {
      val future = downloader.scheduleDownload(targetFile, aromeRun, packageName)
      val result = Await.result(future, 5.minutes)

      logger.info(s"✓ Download successful!")
      logger.info(s"  File: $result")
      logger.info(s"  Size: ${os.size(result) / 1024 / 1024} MB")

      // Try to read GRIB file metadata
      logger.info("\nGRIB file info:")
      val gribInfo = os.proc("wgrib2", "-s", result.toString).call(check = false)
      if (gribInfo.exitCode == 0) {
        logger.info(gribInfo.out.text())
      } else {
        logger.warn("wgrib2 not available, cannot display GRIB metadata")
      }

    } catch {
      case e: Exception =>
        logger.error(s"✗ Download failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }

}
