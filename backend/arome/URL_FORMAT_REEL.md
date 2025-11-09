# AROME France HD - Format Réel des URLs

## Informations clés

- **Modèle** : AROME France HD (haute définition)
- **Résolution** : 1.3 km
- **Base URL** : `https://object.data.gouv.fr/meteofrance-pnt/pnt/`
- **Runs disponibles** : Horaires de 06Z à 21Z (16 runs/jour)

## Structure des URLs

### Pattern général
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/YYYYMMDD/arome/{HH}H/{PACKAGE}_arome-france-hd_{HH}H_YYYYMMDDHH.grib2
```

### Composants
- `YYYYMMDD` : Date du run (ex: `20251015`)
- `{HH}H` : Heure du run avec `H` (ex: `00H`, `06H`, `12H`, `21H`)
- `{PACKAGE}` : Type de données (`SP1`, `SP2`, `SP3`, `HP1`)
- `YYYYMMDDHH` : Date + heure du run (ex: `2025101512`)

## Packages disponibles

### SP1 - Surface Package 1
**Paramètres de surface (niveau 0)**

Contenu :
- Température à 2m (T2M)
- Composantes U/V du vent à 10m
- Pression de surface
- Humidité relative à 2m
- Point de rosée à 2m

**Exemple d'URL (run 12Z du 15/10/2025)** :
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251015/arome/12H/SP1_arome-france-hd_12H_2025101512.grib2
```

---

### SP2 - Surface Package 2
**Couche limite et stabilité atmosphérique**

Contenu :
- CAPE (Convective Available Potential Energy)
- PBLH (Planetary Boundary Layer Height)
- Couverture nuageuse (basse/moyenne/haute)
- Altitude du terrain (géopotentiel de surface)

**Exemple d'URL (run 12Z du 15/10/2025)** :
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251015/arome/12H/SP2_arome-france-hd_12H_2025101512.grib2
```

---

### SP3 - Surface Package 3
**Flux radiatifs et thermiques**

Contenu :
- Flux de chaleur sensible
- Flux de chaleur latente
- Rayonnement solaire descendant (shortwave)
- Autres flux radiatifs

**Exemple d'URL (run 12Z du 15/10/2025)** :
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251015/arome/12H/SP3_arome-france-hd_12H_2025101512.grib2
```

---

### HP1 - Height Package 1
**Vents à plusieurs niveaux au-dessus du sol (AGL)**

Contenu :
- Composantes U/V du vent à multiples hauteurs
- Niveaux disponibles : 20m, 50m, 100m, 150m, 200m, 250m, 500m, 750m, 1000m, 1500m, 2000m, 2500m, 3000m AGL

**Exemple d'URL (run 12Z du 15/10/2025)** :
```
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251015/arome/12H/HP1_arome-france-hd_12H_2025101512.grib2
```

---

## Exemples d'URLs pour différents runs

### Run 06Z (matin)
```bash
# SP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP1_arome-france-hd_06H_2025111506.grib2

# SP2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP2_arome-france-hd_06H_2025111506.grib2

# SP3
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/SP3_arome-france-hd_06H_2025111506.grib2

# HP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/06H/HP1_arome-france-hd_06H_2025111506.grib2
```

### Run 12Z (midi)
```bash
# SP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP1_arome-france-hd_12H_2025111512.grib2

# SP2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP2_arome-france-hd_12H_2025111512.grib2

# SP3
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/SP3_arome-france-hd_12H_2025111512.grib2

# HP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/12H/HP1_arome-france-hd_12H_2025111512.grib2
```

### Run 18Z (soir)
```bash
# SP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP1_arome-france-hd_18H_2025111518.grib2

# SP2
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP2_arome-france-hd_18H_2025111518.grib2

# SP3
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/SP3_arome-france-hd_18H_2025111518.grib2

# HP1
https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/18H/HP1_arome-france-hd_18H_2025111518.grib2
```

## Caractéristiques importantes

### Contenu des fichiers GRIB2

Chaque fichier GRIB contient **tous les horizons de prévision** (0H à 24H) pour le run donné.

- **Pas de téléchargement par heure** : Un seul fichier par package et par run
- **Prévisions horaires** : Fichier contient H+0, H+1, H+2, ..., H+24
- **Taille** : Fichiers volumineux (~50-200 MB par package)

### Mapping vers AromeGrib.scala

Pour utiliser ces packages avec le code existant :

| Ancien | Nouveau | Note |
|--------|---------|------|
| `sp1` | `SP1` | Identique |
| `sp2` | `SP2` | Identique |
| `sp3` | `SP3` | Identique |
| `windsDir/*` | `HP1` | Vents regroupés dans un seul fichier |

### Extraction des données

AromeGrib.scala devra être modifié pour :
1. Lire `HP1` au lieu de multiples fichiers `u_XXXm.grib2` / `v_XXXm.grib2`
2. Extraire les niveaux de vent (20m à 3000m AGL) depuis HP1
3. Sélectionner l'horizon de prévision (H+0, H+1, ..., H+24) dans chaque GRIB

## Commandes de test

### Vérifier la disponibilité (avec curl)

```bash
# Run 12Z d'aujourd'hui
DATE=$(date -u +%Y%m%d)
HOUR="12"

curl -I "https://object.data.gouv.fr/meteofrance-pnt/pnt/${DATE}/arome/${HOUR}H/SP1_arome-france-hd_${HOUR}H_${DATE}${HOUR}.grib2"
```

**Codes de retour** :
- `200 OK` → Fichier disponible
- `404 Not Found` → Fichier non encore publié ou run inexistant

### Tester avec TestDownload.scala

```bash
cd backend

# Test run 12Z d'aujourd'hui
sbt "arome/runMain org.soaringmeteo.arome.TestDownload"

# Test run spécifique
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-15 12"

# Télécharger un fichier test
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-15 12 --download"
```

### Inspecter un fichier GRIB2

```bash
# Lister les variables
wgrib2 -s SP1_arome-france-hd_12H_2025111512.grib2 | head -20

# Compter les records
wgrib2 -count SP1_arome-france-hd_12H_2025111512.grib2

# Extraire metadata
wgrib2 -var -lev SP1_arome-france-hd_12H_2025111512.grib2
```

## Différences avec l'ancien format

| Aspect | Ancien (non fonctionnel) | Nouveau (réel) |
|--------|--------------------------|----------------|
| **Base URL** | `.files.data.gouv.fr` | `.data.gouv.fr` |
| **Structure** | `/{run_time_iso}/arome/{run_number}/` | `/YYYYMMDD/arome/{HH}H/` |
| **Run number** | `001` à `021` (3 chiffres) | `00H` à `21H` (avec H) |
| **Nom fichier** | `arome__005__SP1__00H__...` | `SP1_arome-france-hd_12H_...` |
| **Date format** | ISO 8601 (`2025-11-09T12:00:00Z`) | Compact (`20251109` + `12`) |
| **Packages** | SP1, HP1, HP2, HP3 | SP1, SP2, SP3, HP1 |

## Disponibilité des runs

### Horaire confirmé
- **06Z à 21Z** : 16 runs par jour (toutes les heures)
- **Délai** : Fichiers disponibles ~1-2 heures après l'heure du run

### À tester
Vérifier que tous les runs horaires (06-21) sont effectivement publiés :
```bash
for hour in {06..21}; do
  curl -I "https://object.data.gouv.fr/meteofrance-pnt/pnt/20251115/arome/${hour}H/SP1_arome-france-hd_${hour}H_2025111512.grib2"
done
```

## Références

- **Source officielle** : https://www.data.gouv.fr/fr/datasets/donnees-du-modele-atmospherique-arome-a-aire-limitee-a-haute-resolution/
- **AROME Documentation** : https://donneespubliques.meteofrance.fr/?fond=produit&id_produit=131
- **Modèle AROME** : Modèle atmosphérique à aire limitée français, résolution 1.3 km

## Notes pour le développement

1. **Fichiers uniques** : Pas de loop sur les heures de prévision lors du téléchargement
2. **Lecture GRIB** : Sélectionner l'horizon (H+0, H+1, etc.) lors de l'extraction
3. **Stockage** : ~200-400 MB par run (4 packages × 50-100 MB)
4. **Validation** : Toujours tester les URLs avant déploiement production
