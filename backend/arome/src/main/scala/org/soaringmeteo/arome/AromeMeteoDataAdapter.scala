package org.soaringmeteo.arome

import org.soaringmeteo.{MeteoData, Wind, XCFlyingPotential}
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
import squants.energy.SpecificEnergy
import squants.space.Meters
import java.time.OffsetDateTime

class AromeMeteoDataAdapter(data: AromeData, initTime: OffsetDateTime) extends MeteoData {
  def thermalVelocity = MetersPerSecond(data.thermalVelocity)
  def boundaryLayerDepth = data.pblh
  def wind10m = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
  def windAtHeight(heightMeters: Int) =
    data.windsAtHeights.get(heightMeters).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }

  // Wind methods using AromeData interpolation
  def boundaryLayerWind = wind10m  // Approximation: use 10m wind for boundary layer
  def surfaceWind = wind10m
  def wind300mAGL = {
    data.windAtHeightInterpolated(300.0).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }.getOrElse(wind10m)
  }
  def windSoaringLayerTop = {
    data.windAtHeightInterpolated(data.pblh).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }.getOrElse(wind10m)
  }
  def wind2000mAMSL = {
    data.windAtAltitudeAMSL(2000).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }.getOrElse(wind10m)
  }
  def wind3000mAMSL = {
    data.windAtAltitudeAMSL(3000).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }.getOrElse(wind10m)
  }
  def wind4000mAMSL = {
    data.windAtAltitudeAMSL(4000).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }.getOrElse(wind10m)
  }

  def temperature2m = Kelvin(data.t2m)
  def cape = if (data.cape > 0) SpecificEnergy(data.cape).toOption else None
  def totalCloudCover = (data.cloudCover * 100).toInt
  def time = initTime

  // Additional computed fields
  def soaringLayerDepth = data.pblh  // Use PBLH as soaring layer depth
  def convectiveClouds = None  // AROME lacks dewpoint and isobaric data needed for calculation
  def totalRain = 0.0  // Not available in AROME SP1/SP2/SP3 packages
  def xcFlyingPotential = {
    XCFlyingPotential(
      thermalVelocity,
      Meters(soaringLayerDepth),
      boundaryLayerWind
    )
  }
}
