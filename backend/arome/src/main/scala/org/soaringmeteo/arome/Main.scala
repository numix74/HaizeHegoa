package org.soaringmeteo.arome

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import os.Path
import org.soaringmeteo.MeteoData
import org.soaringmeteo.arome.out.Store

object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    try {
      val settings = Settings.fromCommandLineArguments(args)
      run(settings)
      System.exit(0)
    } catch {
      case e: Exception =>
        logger.error("AROME processing failed", e)
        System.exit(1)
    }
  }

  def run(settings: Settings): Unit = {
    val initTime = OffsetDateTime.now()

    logger.info("=== AROME Production Pipeline ===")
    logger.info(s"Initialization time: $initTime")
    logger.info(s"Zones to process: ${settings.zones.map(_.name).mkString(", ")}")

    Await.result(Store.ensureSchemaExists(), Duration.Inf)

    // Track all processed hours across all zones to determine the forecast extent
    var allProcessedHours = Set.empty[Int]

    for (setting <- settings.zones) {
      logger.info(s"\nProcessing zone: ${setting.name}")
      val outputBaseDir = os.Path(setting.outputDirectory)
      os.makeDir.all(outputBaseDir)
      val processedHours = processZone(initTime, setting, outputBaseDir)
      allProcessedHours = allProcessedHours ++ processedHours
    }

    // Generate forecast.json metadata file
    if (allProcessedHours.nonEmpty) {
      val forecastHours = allProcessedHours.toSeq.sorted
      val versionedTargetDir = os.Path(settings.zones.head.outputDirectory) / ".." / ".."
      logger.info(s"\nWriting forecast metadata to $versionedTargetDir")
      JsonWriter.writeJsons(versionedTargetDir, initTime, settings, forecastHours)
    }

    Store.close()
    logger.info("\n=== AROME Pipeline Complete ===")
  }

  private def processZone(initTime: OffsetDateTime, setting: AromeSetting, outputBaseDir: os.Path): Set[Int] = {
    val gribDir = os.Path(setting.gribDirectory)

    if (!os.exists(gribDir)) {
      logger.warn(s"GRIB directory not found: $gribDir")
      return Set.empty[Int]
    }

    // Définir les groupes de fichiers GRIB
    val groups = Seq(
      ("00H06H", 0 to 6),
      ("07H12H", 7 to 12),
      ("13H18H", 13 to 18),
      ("19H24H", 19 to 24)
      // Ajoutez les autres groupes si vous les avez :
      // ("25H30H", 25 to 30),
      // ("31H36H", 31 to 36),
      // ("37H42H", 37 to 42)
    )

    val futures = scala.collection.mutable.ListBuffer[Future[Unit]]()

    // Traiter chaque groupe de fichiers
    groups.foreach { case (groupName, hours) =>
      logger.info(s"Processing group $groupName (hours ${hours.head}-${hours.last})...")

      val sp1File = gribDir / s"SP1_${groupName}.grib2"
      val sp2File = gribDir / s"SP2_${groupName}.grib2"
      val sp3File = gribDir / s"SP3_${groupName}.grib2"
      val windsDir = gribDir / "winds"

      // Vérifier que tous les fichiers existent
      if (!os.exists(sp1File)) {
        logger.warn(s"Missing file: $sp1File")
        return Set.empty[Int]
      }
      if (!os.exists(sp2File)) {
        logger.warn(s"Missing file: $sp2File")
        return Set.empty[Int]
      }
      if (!os.exists(sp3File)) {
        logger.warn(s"Missing file: $sp3File")
        return Set.empty[Int]
      }

      // Traiter chaque heure du groupe
      hours.foreach { hour =>
        val hourOffsetInGroup = hour - hours.head  // 0, 1, 2, 3, 4, 5, 6 pour le premier groupe

        val future = Future {
          try {
            logger.info(s"  Processing hour $hour (offset $hourOffsetInGroup in file $groupName)...")

            val data = AromeGrib.fromGroupFiles(
              sp1File = sp1File,
              sp2File = sp2File,
              sp3File = sp3File,
              windsDir = windsDir,
              hourOffset = hourOffsetInGroup,  // Offset DANS le fichier groupe
              zone = setting.zone
            )

            val meteoData: IndexedSeq[IndexedSeq[MeteoData]] = data.map { row =>
              row.map { aromeData =>
                new AromeMeteoDataAdapter(aromeData, initTime.plusHours(hour))
              }
            }
            val mapsDir = outputBaseDir / "maps" / f"${hour}%02d"
            os.makeDir.all(mapsDir)
            logger.debug(s"    Generating PNG...")
            org.soaringmeteo.out.Raster.writeAllPngFiles(
              setting.zone.longitudes.size,
              setting.zone.latitudes.size,
              mapsDir,
              hour,
              meteoData
            )
            logger.debug(s"    Generating MVT...")
            org.soaringmeteo.out.VectorTiles.writeAllVectorTiles(
              AromeVectorTilesParameters(setting.zone),
              mapsDir,
              hour,
              meteoData
            )
            logger.debug(s"    Saving to database...")
            Await.result(
              Store.save(initTime, setting.name, hour, data),
              Duration.Inf
            )

            logger.info(s"  Hour $hour completed")
          } catch {
            case e: Exception =>
              logger.error(s"Error processing hour $hour", e)
          }
        }
        futures += future
      }
    }
 
    Await.result(Future.sequence(futures), Duration.Inf)
    logger.info(s"Zone ${setting.name} completed")

    // Return all hours that were processed
    groups.flatMap { case (_, hours) => hours }.toSet
  }
}
