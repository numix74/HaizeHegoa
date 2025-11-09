# AROME HD 0.01° - Data Packages Documentation

## Data Source

- **Provider**: Météo-France via data.gouv.fr
- **Dataset**: Paquets AROME - Résolution 0.01°
- **URL**: https://www.data.gouv.fr/fr/datasets/paquets-arome-resolution-0-01deg/
- **Base download URL**: https://object.files.data.gouv.fr/meteofrance-pnt/pnt/
- **Grid**: EURW1S100 (55.4°N - 37.5°N, 12°W - 16°E)
- **Resolution**: 0.01° (~1.1 km)
- **Format**: GRIB2
- **Update frequency**: 16 runs per day (hourly: 06Z to 21Z)
- **Forecast horizon**: 0-24 hours (hourly)

## URL Pattern

```
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/{run_time}/arome/{run_number}/{package}/arome__{run_number}__{package}__{hour}H__{run_time}.grib2
```

**Example**:
```
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T12:00:00Z/arome/012/HP1/arome__012__HP1__00H__2025-11-09T12:00:00Z.grib2
```

**Parameters**:
- `run_time`: ISO 8601 format (e.g., `2025-11-09T12:00:00Z`)
- `run_number`: Hourly runs from `006` (06Z) to `021` (21Z)
- `package`: `SP1`, `HP1`, `HP2`, or `HP3`
- `hour`: `00H` to `24H` (forecast hour offset)

## Available Packages

### SP1 - Surface Package 1
**Surface-level parameters (first vertical level only)**

Contains essential surface meteorological data:
- **Temperature at 2m** (T2M) - K
- **U-component of wind at 10m** (U10) - m/s
- **V-component of wind at 10m** (V10) - m/s
- **Surface pressure** - Pa
- **Relative humidity at 2m** - %
- **Dewpoint temperature at 2m** - K

**Usage in current code**: Replaces current `sp1` files
**Required for**: Temperature, surface wind, humidity calculations

---

### HP1 - Height Package 1
**Wind components at multiple heights above ground level**

Contains U and V wind components at standard heights:
- Heights: 10m, 20m, 50m, 100m, 150m, 200m, 250m, 500m, 750m, 1000m, 1500m, 2000m, 2500m, 3000m
- **U-component of wind** at each height - m/s
- **V-component of wind** at each height - m/s

**Usage in current code**: Provides wind profiles for `windsAtHeights` in `AromeData`
**Required for**: Wind interpolation at arbitrary heights, wind shear analysis

---

### HP2 - Height Package 2
**Thermodynamic parameters at multiple heights**

Contains atmospheric stability and thermal parameters:
- **Temperature** at multiple heights - K
- **Relative humidity** at multiple heights - %
- **Geopotential height** - m
- **Vertical velocity (omega)** - Pa/s

**Usage in current code**: Could enhance `convectiveClouds` calculation if vertical profiles implemented
**Optional but useful for**: Improved thermal forecasting, cloud base/top estimation

---

### HP3 - Height Package 3
**Additional atmospheric parameters**

Contains specialized meteorological data:
- **CAPE** (Convective Available Potential Energy) - J/kg
- **CIN** (Convective Inhibition) - J/kg
- **Planetary Boundary Layer Height (PBLH)** - m
- **Total cloud cover** - fraction (0-1)
- **Low/medium/high cloud cover** - fraction (0-1)
- **Sensible heat flux** - W/m²
- **Latent heat flux** - W/m²
- **Solar radiation (downward shortwave)** - W/m²
- **Precipitation** - kg/m² (mm)

**Usage in current code**: Replaces current `sp2` and `sp3` files
**Required for**: CAPE, PBLH, cloud cover, heat fluxes, thermal velocity calculation

---

## Mapping to Current Implementation

### Current AromeGrib.scala expects:
```scala
fromGroupFiles(
  sp1File: os.Path,    // Temperature, U10, V10
  sp2File: os.Path,    // CAPE, PBLH, clouds, terrain
  sp3File: os.Path,    // Sensible/latent heat flux, solar radiation
  windsDir: os.Path    // Wind profiles at multiple heights
)
```

### New data.gouv.fr package mapping:
| Current | New Package | Parameters |
|---------|-------------|------------|
| `sp1` | `SP1` | T2M, U10, V10, surface pressure, RH |
| `sp2` + `sp3` | `HP3` | CAPE, PBLH, cloud cover, heat fluxes, solar radiation |
| `windsDir/*` | `HP1` | Wind profiles (U/V at multiple heights) |
| *(future)* | `HP2` | Temperature/humidity profiles (for enhanced convective cloud calculations) |

## Required Updates to AromeGrib.scala

To support the new package structure, `AromeGrib.scala` will need to be modified:

1. **Change method signature**:
```scala
def fromGroupFiles(
  sp1File: os.Path,    // Now from data.gouv.fr SP1
  hp1File: os.Path,    // New: replaces windsDir
  hp3File: os.Path,    // New: replaces sp2 + sp3
  hourOffset: Int,
  zone: AromeZone
): IndexedSeq[IndexedSeq[AromeData]]
```

2. **Extract wind profiles from HP1** instead of separate u_XXXm.grib2 / v_XXXm.grib2 files

3. **Extract CAPE, PBLH, clouds, fluxes from HP3** instead of sp2 + sp3

4. **Optionally use HP2** for temperature/humidity profiles to improve `ConvectiveClouds` calculation

## Download Strategy

### Minimal Download (essential parameters only)
Download **SP1 + HP1 + HP3** for each forecast hour (0-24):
- **Total files per run**: 3 packages × 25 hours = **75 files**
- **File size estimate**: ~10-50 MB per file
- **Total download size**: ~1-4 GB per run

### Full Download (including HP2 for research)
Download **SP1 + HP1 + HP2 + HP3**:
- **Total files per run**: 4 packages × 25 hours = **100 files**
- **Total download size**: ~1.5-5 GB per run

### Recommended Approach
1. Start with **SP1 + HP1 + HP3** only (minimal)
2. Test pipeline with real data
3. Add **HP2** later if vertical profiles improve forecasts

## Parameter Documentation

For detailed parameter descriptions, see:
- **Official PDF**: https://static.data.gouv.fr/resources/paquets-arome-resolution-0-01deg/20241127-114451/description-parametres-modeles-arpege-arome-v2-185.pdf
- **CSV metadata**: https://www.data.gouv.fr/api/1/datasets/r/3aa3ce62-1f69-4ea1-8157-a53eac61c6bb

## Example Usage

### Test URL Availability
```scala
import org.soaringmeteo.arome.in.{AromeRun, AromeDownloader}

val run = AromeRun(2025, 11, 9, 12)  // Nov 9, 2025, 12Z
val availability = AromeDownloader.testRunAvailability(run, hourOffset = 0)

println(availability)
// Map(SP1 -> true, HP1 -> true, HP2 -> true, HP3 -> true)
```

### Download All Packages for Hour 0
```scala
val downloader = new AromeDownloader(downloadRateLimit = 60)
val baseDir = os.Path("/mnt/data/arome/grib")

val futureFiles = downloader.scheduleDownloadAllPackages(
  baseDir = baseDir,
  aromeRun = run,
  hourOffset = 0
)

futureFiles.onComplete {
  case Success(files) =>
    println(s"Downloaded: ${files.mkString(", ")}")
  case Failure(error) =>
    println(s"Download failed: $error")
}
```

### Download Specific Package
```scala
val sp1File = baseDir / run.fileName("SP1", 0)
downloader.scheduleDownload(sp1File, run, "SP1", 0)
```

## Grid Specifications - EURW1S100

**Coverage**:
- North: 55.4°N
- South: 37.5°N
- West: 12°W
- East: 16°E

**Resolution**: 0.01° (~1.1 km)

**Grid dimensions**:
- Longitude points: (16 - (-12)) / 0.01 = **2800 points**
- Latitude points: (55.4 - 37.5) / 0.01 = **1790 points**

**Pays Basque HD subset**:
- lon-min: -2.0°, lon-max: 0.5° → **250 points** (0.01° step)
- lat-min: 42.8°, lat-max: 43.6° → **80 points** (0.01° step)
- Total grid: **250 × 80 = 20,000 points** (vs 14,400 at 0.025°)

## Migration Checklist

- [x] Create `AromeRun.scala` for run metadata
- [x] Create `AromeDownloader.scala` for GRIB2 download
- [x] Update `Settings.scala` with download config
- [x] Update `reference.conf` for 0.01° resolution
- [ ] Update `AromeGrib.scala` to read SP1/HP1/HP3 instead of sp1/sp2/sp3
- [ ] Test download with real data.gouv.fr URLs
- [ ] Validate GRIB2 parameter extraction
- [ ] Update database schema if needed (larger grid = more data)
- [ ] Update frontend to display "AROME HD 1.1km" instead of "2.5km"

## Notes

1. **Data Availability**: AROME files are typically available 1-2 hours after run initialization
2. **Rate Limiting**: Respect data.gouv.fr limits (recommended: 60 req/min)
3. **Storage**: HD resolution requires ~4x more storage than 2.5km version
4. **Processing Time**: Larger grids will increase computation time for PNG/MVT generation
5. **Network**: Each run download is ~1-4 GB, plan bandwidth accordingly
