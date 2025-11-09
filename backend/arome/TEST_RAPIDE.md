# AROME - Guide de Test Rapide

## ✅ URLs corrigées !

Les URLs ont été mises à jour pour correspondre à la **structure réelle** de data.gouv.fr.

### Format corrigé
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP1_arome-france-hd_12H_2025111512.grib2
```

## 🧪 Tests rapides

### 1. Tester avec curl (run 12Z d'aujourd'hui)

```bash
# SP1 - Paramètres de surface
curl -I https://object.data.gouv.fr/meteofrance-pnt/pnt/$(date -u +%Y%m%d)/arome/12H/SP1_arome-france-hd_12H_$(date -u +%Y%m%d)12.grib2

# SP2 - Couche limite (CAPE, PBLH)
curl -I https://object.data.gouv.fr/meteofrance-pnt/pnt/$(date -u +%Y%m%d)/arome/12H/SP2_arome-france-hd_12H_$(date -u +%Y%m%d)12.grib2

# SP3 - Flux radiatifs
curl -I https://object.data.gouv.fr/meteofrance-pnt/pnt/$(date -u +%Y%m%d)/arome/12H/SP3_arome-france-hd_12H_$(date -u +%Y%m%d)12.grib2

# HP1 - Vents multi-niveaux
curl -I https://object.data.gouv.fr/meteofrance-pnt/pnt/$(date -u +%Y%m%d)/arome/12H/HP1_arome-france-hd_12H_$(date -u +%Y%m%d)12.grib2
```

**Attendu** :
- `HTTP/1.1 200 OK` → Fichier disponible ✅
- `HTTP/1.1 404 Not Found` → Run pas encore publié (trop récent)

### 2. Tester avec TestDownload.scala

```bash
cd /home/user/HaizeHegoa/backend

# Test auto (détecte le dernier run disponible)
sbt "arome/runMain org.soaringmeteo.arome.TestDownload"

# Test run spécifique (15 nov 2025, 12Z)
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-15 12"

# Télécharger un fichier test
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-15 12 --download"
```

### 3. Tester tous les runs horaires (06Z-21Z)

```bash
# Boucle sur toutes les heures
DATE=$(date -u +%Y%m%d)

for HOUR in {06..21}; do
  echo "Testing ${HOUR}H..."
  STATUS=$(curl -o /dev/null -s -w "%{http_code}" "https://object.data.gouv.fr/meteofrance-pnt/pnt/${DATE}/arome/${HOUR}H/SP1_arome-france-hd_${HOUR}H_${DATE}${HOUR}.grib2")

  if [ "$STATUS" -eq 200 ]; then
    echo "  ✓ ${HOUR}H: AVAILABLE"
  else
    echo "  ✗ ${HOUR}H: NOT FOUND ($STATUS)"
  fi
done
```

## 📦 Packages AROME

### SP1 - Surface Package 1
- **Contenu** : Température 2m, Vent 10m, Pression surface, Humidité
- **Taille** : ~50-100 MB
- **Utilisation** : Paramètres de surface pour affichage météo

### SP2 - Surface Package 2
- **Contenu** : CAPE, PBLH, Couverture nuageuse, Terrain
- **Taille** : ~30-80 MB
- **Utilisation** : Thermiques et stabilité atmosphérique

### SP3 - Surface Package 3
- **Contenu** : Flux chaleur sensible/latente, Rayonnement solaire
- **Taille** : ~30-70 MB
- **Utilisation** : Calcul vitesse thermique (w*)

### HP1 - Height Package 1
- **Contenu** : Vents U/V à 20m, 50m, 100m, ..., 3000m AGL
- **Taille** : ~80-150 MB
- **Utilisation** : Profils de vent en altitude

## 📊 Exemples d'URLs valides

### Run 06Z (matin)
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP1_arome-france-hd_06H_2025111506.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP2_arome-france-hd_06H_2025111506.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP3_arome-france-hd_06H_2025111506.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/HP1_arome-france-hd_06H_2025111506.grib2
```

### Run 12Z (midi)
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP1_arome-france-hd_12H_2025111512.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP2_arome-france-hd_12H_2025111512.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP3_arome-france-hd_12H_2025111512.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/HP1_arome-france-hd_12H_2025111512.grib2
```

### Run 18Z (soir)
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP1_arome-france-hd_18H_2025111518.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP2_arome-france-hd_18H_2025111518.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP3_arome-france-hd_18H_2025111518.grib2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/HP1_arome-france-hd_18H_2025111518.grib2
```

## 🔍 Inspection d'un fichier GRIB2

Une fois téléchargé, inspectez le contenu :

```bash
# Lister toutes les variables
wgrib2 -s SP1_arome-france-hd_12H_2025111512.grib2 | head -20

# Compter les records GRIB
wgrib2 -count SP1_arome-france-hd_12H_2025111512.grib2

# Variables et niveaux
wgrib2 -var -lev SP1_arome-france-hd_12H_2025111512.grib2 | sort | uniq

# Extraire une variable spécifique
wgrib2 SP1_arome-france-hd_12H_2025111512.grib2 -match "TMP:2 m above ground"
```

## ⚙️ Configuration pour tests

### Activer le téléchargement automatique

Éditez `backend/arome/src/main/resources/reference.conf` :

```hocon
arome {
  enable-download = true   # Activer le téléchargement
  download-rate-limit = 60
  run-init-time = "12"     # Forcer run 12Z (optionnel)

  zones = [{
    name = "Pays Basque HD"
    step = 0.01  # ~1.3 km
    # ...
  }]
}
```

### Tester le pipeline complet

```bash
# Créer un fichier de config test
cat > /tmp/arome-test.conf <<EOF
arome {
  enable-download = true
  download-rate-limit = 60
  run-init-time = "12"

  zones = [{
    name = "Test"
    lon-min = -1.0
    lon-max = 0.0
    lat-min = 43.0
    lat-max = 43.5
    step = 0.01
    grib-directory = "/tmp/arome-test/grib"
    output-directory = "/tmp/arome-test/output"
  }]
}
EOF

# Lancer le pipeline
sbt "arome/runMain org.soaringmeteo.arome.Main /tmp/arome-test.conf"
```

## ❓ Troubleshooting

### Erreur 404

**Cause** : Run pas encore publié ou heure invalide

**Solutions** :
1. Attendre 1-2h après l'heure du run
2. Tester avec un run plus ancien (hier)
3. Vérifier que l'heure est entre 06Z et 21Z

### Erreur de timeout

**Cause** : Fichiers volumineux, connexion lente

**Solutions** :
1. Augmenter `readTimeout` dans AromeDownloader
2. Réduire `downloadRateLimit` pour laisser plus de temps
3. Télécharger un seul package à la fois

### wgrib2 not found

**Installation** :
```bash
# Ubuntu/Debian
sudo apt-get install wgrib2

# OU compiler depuis source
wget https://www.ftp.cpc.ncep.noaa.gov/wd51we/wgrib2/wgrib2.tgz
tar -xvf wgrib2.tgz
cd grib2
make
```

## 📚 Documentation complète

- **URL_FORMAT_REEL.md** : Format complet des URLs
- **AROME_PACKAGES.md** : Description des packages
- **RUNS_HORAIRES.md** : Configuration runs horaires 06Z-21Z
- **MIGRATION_GUIDE.md** : Guide de migration

## ✅ Checklist de validation

- [ ] curl retourne `200 OK` pour au moins un run
- [ ] TestDownload.scala affiche "AVAILABLE" pour les 4 packages
- [ ] Téléchargement d'un fichier réussit
- [ ] wgrib2 peut lire le fichier
- [ ] Fichier contient bien 25 horizons de prévision (H+0 à H+24)

## 🚀 Prochaines étapes

1. ✅ Valider les URLs avec curl
2. ✅ Télécharger un fichier test
3. ⏳ Adapter AromeGrib.scala pour lire les nouveaux packages
4. ⏳ Tester le pipeline complet
5. ⏳ Déployer en production
