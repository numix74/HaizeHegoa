# AROME 2.5 km - Flux Complet et Intégration

## Vue d'ensemble

Ce document décrit l'intégration complète du modèle AROME 2.5 km dans l'application SoaringMeteo, incluant les modifications apportées au backend, à nginx et l'affichage dans le frontend.

## Architecture du Flux

```
┌─────────────────────────────────────────────────────────┐
│ 1. Backend calcule minViewZoom                         │
│    - GFS: maxViewZoom = 8                              │
│    - WRF 2km: maxViewZoom = 12                         │
│    - WRF 6km: maxViewZoom = 10                         │
│    - AROME 2.5km: maxViewZoom = 12 ✅ (NOUVEAU)       │
│    - minViewZoom = max(maxViewZoom - zoomLevels + 1, 0)│
│                                                         │
│    → Écrit dans forecast.json                          │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│ 2. Frontend charge forecast.json                       │
│    → Récupère vectorTiles.minZoom                      │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│ 3. App.tsx appelle setWindLayerSource()                │
│    → Passe minZoom à la carte OpenLayers               │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│ 4. OpenLayers applique setMinZoom()                    │
│    → Masque la couche si zoom < minZoom                │
│    → Affiche la couche si zoom ≥ minZoom               │
└─────────────────────────────────────────────────────────┘
```

## Modifications Apportées

### 1. Backend - AromeVectorTilesParameters.scala

**Fichier**: `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeVectorTilesParameters.scala`

**Modification**:
```scala
// AVANT: maxViewZoom = 8 (comme GFS)
VectorTiles.Parameters(extent, 8, zone.longitudes.size, zone.latitudes.size, coordinates)

// APRÈS: maxViewZoom = 12 (adapté pour AROME 2.5 km)
VectorTiles.Parameters(extent, 12, zone.longitudes.size, zone.latitudes.size, coordinates)
```

**Effet**:
- AROME 2.5 km a maintenant le même maxViewZoom que WRF 2 km (résolutions similaires)
- minViewZoom calculé automatiquement : ~9 (selon zoomLevels)
- Les tuiles vectorielles s'affichent à partir du niveau de zoom 9

### 2. Backend - JsonWriter.scala (NOUVEAU)

**Fichier**: `backend/arome/src/main/scala/org/soaringmeteo/arome/JsonWriter.scala`

**Fonctionnalité**:
- Génère le fichier `forecast.json` contenant les métadonnées des prévisions AROME
- Structure identique à GFS et WRF pour la compatibilité frontend
- Inclut :
  - Zones couvertes (id, label, raster, vectorTiles)
  - Prévisions disponibles (path, init, first, latest, zones)
  - Paramètres des tuiles vectorielles (extent, zoomLevels, minZoom, tileSize)

**Format du forecast.json**:
```json
{
  "zones": [
    {
      "id": "pays_basque",
      "label": "Pays_basque",
      "raster": {
        "proj": "EPSG:4326",
        "resolution": 0.025,
        "extent": [-2.0125, 42.7875, 0.5125, 43.6125]
      },
      "vectorTiles": {
        "extent": [-223000, 5318000, 55000, 5422000],
        "zoomLevels": 4,
        "minZoom": 9,
        "tileSize": 512
      }
    }
  ],
  "forecasts": [
    {
      "path": "2025-11-12T06",
      "init": "2025-11-12T06:00:00Z",
      "latest": 24,
      "zones": ["pays_basque"]
    }
  ]
}
```

### 3. Backend - Main.scala

**Fichier**: `backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala`

**Modifications**:
1. La méthode `run()` collecte maintenant toutes les heures traitées
2. Appel de `JsonWriter.writeJsons()` après le traitement de toutes les zones
3. La méthode `processZone()` retourne maintenant `Set[Int]` (heures traitées)

**Code ajouté**:
```scala
// Génération forecast.json metadata file
if (allProcessedHours.nonEmpty) {
  val forecastHours = allProcessedHours.toSeq.sorted
  val versionedTargetDir = os.Path(settings.zones.head.outputDirectory) / ".." / ".."
  logger.info(s"\nWriting forecast metadata to $versionedTargetDir")
  JsonWriter.writeJsons(versionedTargetDir, initTime, settings, forecastHours)
}
```

### 4. Nginx - Configuration

**Fichier**: `nginx/arome.conf`

**Routes ajoutées**:
```nginx
# Données statiques AROME (PNG, MVT)
location ~ ^/v2/data/7/arome/(.*)$ {
    alias /mnt/soaringmeteo-data/arome/output/$1;
    expires 1h;
    add_header Cache-Control "public, max-age=3600";
}

# Pas de cache pour forecast.json d'AROME
location ~ ^/v2/data/7/arome/forecast.json$ {
    alias /mnt/soaringmeteo-data/arome/output/forecast.json;
    expires -1;
    add_header Cache-Control "no-store, no-cache, must-revalidate";
    add_header Pragma "no-cache";
}
```

**URLs accessibles**:
- `http://51.254.207.208/v2/data/7/arome/forecast.json`
- `http://51.254.207.208/v2/data/7/arome/2025-11-12T06/pays_basque/maps/00/thermal-velocity.png`
- `http://51.254.207.208/v2/data/7/arome/2025-11-12T06/pays_basque/maps/00/wind-surface/0-0-0.mvt`

### 5. Frontend - Aucune modification nécessaire

Le frontend était déjà prêt pour AROME !

**Fichier**: `frontend/src/App.tsx`
```typescript
// Charge automatiquement les 3 modèles
Promise.all([
  fetchGfsForecastRuns(),
  fetchWrfForecastRuns(),
  fetchAromeForecastRuns()  // ✅ Déjà implémenté
])
```

**Fichier**: `frontend/src/data/ForecastMetadata.ts`
```typescript
export const fetchAromeForecastRuns = async (): Promise<[Array<ForecastMetadata>, Array<Zone>]> =>
  fetchForecastRuns('arome');  // ✅ Charge /v2/data/7/arome/forecast.json
```

## Structure des Fichiers

```
/mnt/soaringmeteo-data/arome/
├── grib/
│   └── pays_basque/
│       ├── SP1_00H06H.grib2
│       ├── SP2_00H06H.grib2
│       ├── SP3_00H06H.grib2
│       └── winds/
│           ├── WIND_100m_00H06H.grib2
│           ├── WIND_250m_00H06H.grib2
│           └── ...
└── output/
    ├── forecast.json  ✅ Généré par JsonWriter
    └── 7/  (version du format)
        └── 2025-11-12T06/  (run)
            └── pays_basque/  (zone)
                └── maps/
                    ├── 00/  (heure)
                    │   ├── thermal-velocity.png
                    │   ├── boundary-layer.png
                    │   ├── wind-surface/
                    │   │   ├── 0-0-0.mvt
                    │   │   └── ...
                    │   └── ...
                    ├── 01/
                    └── ...
```

## Calcul du minViewZoom

Pour AROME 2.5 km avec maxViewZoom = 12 :

```scala
val threshold = 15  // max points par tuile
var zoomLevelsValue = 1
var maxPoints = math.max(width, height)

while (maxPoints > threshold) {
  maxPoints = maxPoints / 2
  zoomLevelsValue = zoomLevelsValue + 1
}

val minViewZoomValue = math.max(maxViewZoom - zoomLevelsValue + 1, 0)
```

**Exemple pour le Pays Basque** :
- Grille : 100 × 33 points (lon × lat)
- maxPoints = max(100, 33) = 100
- Itérations :
  - maxPoints = 100 / 2 = 50, zoomLevels = 2
  - maxPoints = 50 / 2 = 25, zoomLevels = 3
  - maxPoints = 25 / 2 = 12, zoomLevels = 4
- **minViewZoom = 12 - 4 + 1 = 9**

## Déploiement

### Script automatique

```bash
cd /home/user/HaizeHegoa
chmod +x scripts/deploy_arome_2.5km.sh
./scripts/deploy_arome_2.5km.sh
```

### Étapes manuelles

1. **Compiler le backend**:
   ```bash
   cd /home/ubuntu/soaringmeteo/backend
   sbt "arome/compile"
   ```

2. **Configurer nginx**:
   ```bash
   sudo nano /etc/nginx/sites-available/default
   # Ajouter le contenu de nginx/arome.conf
   sudo nginx -t
   sudo systemctl reload nginx
   ```

3. **Exécuter le pipeline**:
   ```bash
   cd /home/ubuntu/soaringmeteo/backend
   bash scripts/arome_daily_pipeline.sh
   ```

4. **Vérifier**:
   ```bash
   # Vérifier forecast.json
   curl http://localhost/v2/data/7/arome/forecast.json | jq .

   # Vérifier les cartes
   ls -la /mnt/soaringmeteo-data/arome/output/*/pays_basque/maps/
   ```

## Tests

### 1. Vérifier la génération de forecast.json

```bash
# Sur le VPS
cat /mnt/soaringmeteo-data/arome/output/forecast.json

# Devrait contenir :
# - "zones": [ { "id": "pays_basque", ... } ]
# - "forecasts": [ { "path": "2025-11-12T06", ... } ]
# - "vectorTiles": { "minZoom": 9, ... }
```

### 2. Vérifier l'accès web

```bash
# Depuis le VPS
curl -I http://localhost/v2/data/7/arome/forecast.json
# Devrait retourner: HTTP/1.1 200 OK

# Depuis votre machine
curl http://51.254.207.208/v2/data/7/arome/forecast.json
```

### 3. Vérifier le frontend

1. Ouvrir `http://51.254.207.208/v2/` dans un navigateur
2. Ouvrir la console (F12) → Network
3. Vérifier que `/v2/data/7/arome/forecast.json` est chargé avec succès (200)
4. Le modèle AROME devrait apparaître dans le sélecteur de modèles
5. Zoomer sur le Pays Basque
6. À partir du zoom 9, les flèches de vent devraient s'afficher

## Dépannage

### forecast.json n'est pas généré

**Vérifier les logs**:
```bash
tail -f /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
```

**Vérifier que JsonWriter est bien appelé**:
```bash
# Dans les logs, rechercher :
grep "Writing forecast metadata" /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
```

### Le frontend ne charge pas AROME

1. **Vérifier forecast.json**:
   ```bash
   curl http://51.254.207.208/v2/data/7/arome/forecast.json
   ```

2. **Console du navigateur**:
   - F12 → Network → Filter "arome"
   - Vérifier le statut HTTP (doit être 200)
   - Vérifier le contenu JSON

3. **Vérifier nginx**:
   ```bash
   sudo nginx -t
   sudo tail -f /var/log/nginx/error.log
   ```

### Les tuiles ne s'affichent pas

1. **Vérifier minZoom dans forecast.json**:
   ```bash
   cat /mnt/soaringmeteo-data/arome/output/forecast.json | jq '.zones[0].vectorTiles.minZoom'
   # Devrait afficher: 9
   ```

2. **Vérifier le niveau de zoom dans le frontend**:
   - Ouvrir la console (F12)
   - Zoomer sur la carte
   - Les tuiles devraient apparaître à partir du zoom 9

3. **Vérifier que les fichiers MVT existent**:
   ```bash
   find /mnt/soaringmeteo-data/arome/output -name "*.mvt" | head
   ```

## Avantages de cette implémentation

✅ **Backend**:
- maxViewZoom adapté à la résolution 2.5 km
- Génération automatique de forecast.json
- Compatibilité avec le format existant (version 7)

✅ **Frontend**:
- Aucune modification nécessaire
- Chargement automatique d'AROME
- Affichage optimal avec minZoom = 9

✅ **Nginx**:
- Routes simples et cohérentes avec GFS/WRF
- Cache optimisé (1h pour les données, pas de cache pour forecast.json)

✅ **Déploiement**:
- Script automatique fourni
- Documentation complète
- Configuration par fichier .conf

## Prochaines améliorations possibles

1. **Location Forecasts** : Implémenter la génération des fichiers `/locations/{x}-{y}.json` pour les prévisions détaillées par point
2. **Nettoyage automatique** : Ajouter la suppression des anciennes prévisions (> 2 jours)
3. **Métriques** : Ajouter des logs de performance et monitoring
4. **Zones supplémentaires** : Étendre à d'autres régions (Pyrénées, Alpes, etc.)

## Références

- **Backend AROME**: `backend/arome/src/main/scala/org/soaringmeteo/arome/`
- **Configuration nginx**: `nginx/arome.conf`
- **Frontend**: `frontend/src/data/ForecastMetadata.ts`
- **Documentation**: `nginx/README-AROME-NGINX.md`
- **Script de déploiement**: `scripts/deploy_arome_2.5km.sh`
