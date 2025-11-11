package org.soaringmeteo.arome

import org.soaringmeteo.out.formatVersion

package object out {

  /** Directory to write the output of the AROME forecast and the
   * `forecast.json` metadata.
   */
  def versionedTargetPath(basePath: os.Path): os.Path =
    basePath / formatVersion.toString / "arome"

  /** Directory to write the output of an AROME run */
  def runTargetPath(versionedTargetPath: os.Path, initDateString: String): os.Path =
    versionedTargetPath / initDateString

  /** Directory to write the output of a zone */
  def zoneTargetPath(runTargetPath: os.Path, zoneId: String): os.Path =
    runTargetPath / zoneId

}
