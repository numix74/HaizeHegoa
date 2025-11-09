# AROME HD 0.01° Migration Guide

## Quick Start

### Step 1: Test URL Availability

First, verify that data.gouv.fr URLs are accessible:

```bash
cd backend
sbt "arome/runMain org.soaringmeteo.arome.TestDownload"
```

Expected output:
```
Checking availability for hour 0...
  SP1 : ✓ AVAILABLE
  HP1 : ✓ AVAILABLE
  HP2 : ✓ AVAILABLE
  HP3 : ✓ AVAILABLE
```

### Step 2: Test Single File Download

Download one file to verify the pipeline works:

```bash
sbt "arome/runMain org.soaringmeteo.arome.TestDownload --download"
```

This will download `SP1` package for hour 0 to `test-download/arome/`.

### Step 3: Update Configuration

Edit your production config file (or use `reference.conf`):

```hocon
arome {
  enable-download = true  # Enable automatic download
  download-rate-limit = 60
  run-init-time = "12"    # Optional: force 12Z run

  zones = [{
    name = "Pays Basque HD"
    step = 0.01  # HD resolution
    # ... other settings
  }]
}
```

### Step 4: Update AromeGrib.scala (TODO)

**Current implementation needs modification** to read from new packages:

```scala
// OLD (current)
fromGroupFiles(
  sp1File = ...,
  sp2File = ...,
  sp3File = ...,
  windsDir = ...
)

// NEW (required)
fromGroupFiles(
  sp1File = ...,  // from data.gouv.fr SP1
  hp1File = ...,  // from data.gouv.fr HP1 (wind profiles)
  hp3File = ...,  // from data.gouv.fr HP3 (CAPE, PBLH, fluxes)
  hourOffset = ...,
  zone = ...
)
```

**Changes needed in AromeGrib.scala**:

1. Remove `sp2File` and `sp3File` parameters
2. Remove `windsDir` parameter
3. Add `hp1File` and `hp3File` parameters
4. Update GRIB variable extraction to match new package structure
5. Extract wind profiles from HP1 instead of separate files

See `AROME_PACKAGES.md` for detailed parameter mapping.

## Full Migration Steps

### 1. Backup Current System

```bash
# Backup current GRIB files
cp -r /mnt/soaringmeteo-data/arome/grib /mnt/soaringmeteo-data/arome/grib.backup

# Backup current code
git checkout -b backup-before-arome-hd
git commit -am "Backup before AROME HD 0.01° migration"
```

### 2. Update Dependencies (if needed)

Check that your `build.sbt` includes required libraries:
- `requests` (for HTTP downloads)
- `grib` / NetCDF libraries (for GRIB2 reading)

### 3. Modify AromeGrib.scala

**File**: `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeGrib.scala`

**Required changes**:

#### A. Update method signature
```scala
def fromGroupFiles(
  sp1File: os.Path,    // SP1 from data.gouv.fr
  hp1File: os.Path,    // HP1 from data.gouv.fr (wind profiles)
  hp3File: os.Path,    // HP3 from data.gouv.fr (CAPE, PBLH, fluxes)
  hourOffset: Int,
  zone: AromeZone
): IndexedSeq[IndexedSeq[AromeData]]
```

#### B. Update SP1 extraction (minimal changes)
```scala
// SP1 structure should be similar to current sp1
val sp1Data = Grib.bracket(sp1File) { grib =>
  val t2m = grib.Feature("Temperature_height_above_ground")
  val u10 = grib.Feature("u-component_of_wind_height_above_ground")
  val v10 = grib.Feature("v-component_of_wind_height_above_ground")
  // ... same as before
}
```

#### C. Replace sp2 + sp3 with HP3
```scala
// HP3 contains CAPE, PBLH, clouds, heat fluxes, solar radiation
val hp3Data = Grib.bracket(hp3File) { grib =>
  val cape = grib.Feature.maybe("Convective_available_potential_energy_surface")
  val pblh = grib.Feature.maybe("Planetary_boundary_layer_height_surface")
  val cloudCover = grib.Feature.maybe("Total_cloud_cover_surface")
  val sensibleHeat = grib.Feature.maybe("Sensible_heat_net_flux_surface")
  val latentHeat = grib.Feature.maybe("Latent_heat_net_flux_surface")
  val solarRad = grib.Feature.maybe("Downward_short_wave_radiation_flux_surface")
  val terrain = grib.Feature.maybe("Geopotential_height_surface")

  // Return tuple of all parameters
  (cape, pblh, cloudCover, sensibleHeat, latentHeat, solarRad, terrain)
}
```

**Note**: Exact GRIB parameter names need to be verified by inspecting actual HP3 files with `wgrib2 -s`.

#### D. Replace windsDir with HP1
```scala
// HP1 contains all wind levels in one file
val hp1Data = Grib.bracket(hp1File) { grib =>
  heightLevels.map { height =>
    val u = grib.Feature.maybe(s"u-component_of_wind_height_above_ground_${height}m")
    val v = grib.Feature.maybe(s"v-component_of_wind_height_above_ground_${height}m")
    height -> (u, v)
  }.toMap
}
```

**Note**: Parameter naming convention needs verification with actual HP1 files.

### 4. Update Main.scala

**File**: `backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala`

Add download logic before processing:

```scala
def run(settings: Settings): Unit = {
  val aromeRun = AromeRun.findLatest(Settings.aromeRunInitTime)

  logger.info(s"Using AROME run: ${aromeRun.initDateTime}")

  // Download GRIBs if enabled
  if (Settings.enableDownload) {
    logger.info("Downloading AROME GRIB files...")
    downloadGribFiles(aromeRun, settings)
  }

  // Existing processing logic
  for (setting <- settings.zones) {
    processZone(aromeRun.initDateTime, setting, ...)
  }
}

private def downloadGribFiles(aromeRun: AromeRun, settings: Settings): Unit = {
  val downloader = new AromeDownloader(Settings.downloadRateLimit)

  for (setting <- settings.zones) {
    val gribDir = os.Path(setting.gribDirectory)
    os.makeDir.all(gribDir)

    logger.info(s"Downloading for zone: ${setting.name}")

    for (hourOffset <- 0 until 25) {
      val packageFiles = Await.result(
        downloader.scheduleDownloadAllPackages(gribDir, aromeRun, hourOffset),
        Duration.Inf
      )
      logger.info(s"  Hour $hourOffset: ${packageFiles.keys.mkString(", ")}")
    }
  }
}
```

### 5. Test with Real Data

```bash
# Test download
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-09 12 --download"

# Inspect GRIB file
wgrib2 -s test-download/arome/arome__005__SP1__00H__2025-11-09T12:00:00Z.grib2 | head -20

# Test full pipeline (once AromeGrib.scala is updated)
sbt "arome/runMain org.soaringmeteo.arome.Main config/arome-hd.conf"
```

### 6. Validate Output

After running the pipeline:

```bash
# Check PNG generation
ls /mnt/soaringmeteo-data/arome/output/pays_basque_hd/maps/00/*.png

# Check MVT generation
ls /mnt/soaringmeteo-data/arome/output/pays_basque_hd/maps/00/*.mvt

# Check database
sqlite3 /tmp/arome.h2 "SELECT COUNT(*) FROM arome_data;"
```

### 7. Update Frontend (if needed)

**File**: `frontend/src/LayersSelector.tsx`

Update label to reflect HD resolution:

```typescript
['AROME Pays Basque HD (1.1 km)', () => 'Météo-France AROME HD 0.01°', aromeName]
```

## Troubleshooting

### Issue: 403 Forbidden when downloading

**Solution**: Check data.gouv.fr status, verify URL format, reduce rate limit

### Issue: GRIB parameter names don't match

**Solution**: Use `wgrib2 -s <file>` to list exact parameter names in GRIB file, update AromeGrib.scala accordingly

### Issue: Files too large / slow download

**Solution**:
- Reduce forecast horizon (e.g., 0-12h instead of 0-24h)
- Download only essential packages (SP1 + HP3, skip HP1 if wind profiles not needed)
- Increase download parallelism carefully

### Issue: Grid mismatch

**Solution**: Verify zone configuration matches EURW1S100 grid bounds (55.4°N - 37.5°N, 12°W - 16°E)

## Rollback Plan

If migration fails:

```bash
# Restore code
git checkout main

# Restore GRIB files
rm -rf /mnt/soaringmeteo-data/arome/grib
mv /mnt/soaringmeteo-data/arome/grib.backup /mnt/soaringmeteo-data/arome/grib

# Restore config
git checkout backend/arome/src/main/resources/reference.conf
```

## Performance Considerations

### Storage

- **Old (0.025°)**: 120×120 = 14,400 points per grid
- **New (0.01°)**: 250×80 = 20,000 points per grid
- **Increase**: ~1.4x more data

### Download Time

- **Estimated**: 1-4 GB per run (SP1 + HP1 + HP3, 25 hours)
- **At 10 Mbps**: ~15-60 minutes per run

### Processing Time

- Larger grids will increase PNG/MVT generation time
- Database inserts will take longer
- Consider parallel processing for multiple zones

## Next Steps

1. ✅ Download infrastructure created
2. ✅ Configuration updated for 0.01°
3. ⏳ **Update AromeGrib.scala** (in progress)
4. ⏳ Test with real data
5. ⏳ Validate output quality
6. ⏳ Deploy to production

## Support

For issues or questions:
- Check `AROME_PACKAGES.md` for package details
- Review `TestDownload.scala` for URL patterns
- Consult Météo-France documentation: https://www.data.gouv.fr/fr/datasets/paquets-arome-resolution-0-01deg/
