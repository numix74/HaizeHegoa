# AROME - Runs Horaires 06Z à 21Z

## Configuration

Le système AROME est maintenant configuré pour supporter **16 runs horaires par jour** de **06Z à 21Z**.

## Mapping des Run Numbers

| Heure UTC | Run Number | Exemple URL |
|-----------|------------|-------------|
| 06Z | `006` | `.../arome/006/HP1/arome__006__HP1__00H__...` |
| 07Z | `007` | `.../arome/007/HP1/arome__007__HP1__00H__...` |
| 08Z | `008` | `.../arome/008/HP1/arome__008__HP1__00H__...` |
| 09Z | `009` | `.../arome/009/HP1/arome__009__HP1__00H__...` |
| 10Z | `010` | `.../arome/010/HP1/arome__010__HP1__00H__...` |
| 11Z | `011` | `.../arome/011/HP1/arome__011__HP1__00H__...` |
| 12Z | `012` | `.../arome/012/HP1/arome__012__HP1__00H__...` |
| 13Z | `013` | `.../arome/013/HP1/arome__013__HP1__00H__...` |
| 14Z | `014` | `.../arome/014/HP1/arome__014__HP1__00H__...` |
| 15Z | `015` | `.../arome/015/HP1/arome__015__HP1__00H__...` |
| 16Z | `016` | `.../arome/016/HP1/arome__016__HP1__00H__...` |
| 17Z | `017` | `.../arome/017/HP1/arome__017__HP1__00H__...` |
| 18Z | `018` | `.../arome/018/HP1/arome__018__HP1__00H__...` |
| 19Z | `019` | `.../arome/019/HP1/arome__019__HP1__00H__...` |
| 20Z | `020` | `.../arome/020/HP1/arome__020__HP1__00H__...` |
| 21Z | `021` | `.../arome/021/HP1/arome__021__HP1__00H__...` |

## Utilisation

### Spécifier un run horaire

Dans `reference.conf` ou via ligne de commande :

```hocon
arome {
  run-init-time = "14"  # Utiliser le run de 14Z
}
```

Valeurs acceptées : `"06"`, `"07"`, `"08"`, ..., `"21"`

### Auto-détection du dernier run

Si `run-init-time` n'est pas spécifié, le système détecte automatiquement le run le plus récent :

```scala
val aromeRun = AromeRun.findLatest()  // Trouve le run le plus récent
```

**Logique** :
- Calcule l'heure actuelle moins 2 heures (délai de disponibilité des données)
- Si avant 06Z UTC → utilise 21Z de la veille
- Si entre 06Z et 21Z → utilise l'heure effective
- Si après 21Z → utilise 21Z du jour même

### Créer un run spécifique

```scala
// Run du 9 novembre 2025 à 14Z
val run = AromeRun(2025, 11, 9, 14)

// Génère automatiquement :
// - run_number = "014"
// - URLs avec /arome/014/
```

## Exemples d'URLs Générées

### Run 06Z (matin)
```
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T06:00:00Z/arome/006/SP1/arome__006__SP1__00H__2025-11-09T06:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T06:00:00Z/arome/006/HP1/arome__006__HP1__00H__2025-11-09T06:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T06:00:00Z/arome/006/HP3/arome__006__HP3__00H__2025-11-09T06:00:00Z.grib2
```

### Run 12Z (midi)
```
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T12:00:00Z/arome/012/SP1/arome__012__SP1__00H__2025-11-09T12:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T12:00:00Z/arome/012/HP1/arome__012__HP1__00H__2025-11-09T12:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T12:00:00Z/arome/012/HP3/arome__012__HP3__00H__2025-11-09T12:00:00Z.grib2
```

### Run 18Z (soir)
```
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T18:00:00Z/arome/018/SP1/arome__018__SP1__00H__2025-11-09T18:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T18:00:00Z/arome/018/HP1/arome__018__HP1__00H__2025-11-09T18:00:00Z.grib2
https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-09T18:00:00Z/arome/018/HP3/arome__018__HP3__00H__2025-11-09T18:00:00Z.grib2
```

## Avantages des Runs Horaires

### 1. **Mises à jour plus fréquentes**
- Anciennement : 8 runs/jour (toutes les 3h)
- Maintenant : **16 runs/jour (toutes les heures)** de 06Z à 21Z
- Amélioration : **2x plus de mises à jour** pendant la journée

### 2. **Meilleure réactivité**
- Délai max entre runs : **1 heure** (au lieu de 3 heures)
- Prévisions plus récentes pour les vols de la journée
- Capture mieux les évolutions rapides du temps

### 3. **Couverture optimale pour le vol à voile**
- 06Z = 7h heure locale (FR) → début de journée
- 21Z = 22h heure locale (FR) → fin de journée
- Couvre toute la fenêtre de vol diurne

### 4. **Flexibilité accrue**
- Possibilité de choisir le run le plus adapté
- Comparaison entre plusieurs runs horaires
- Affinage des prévisions en cours de journée

## Impact sur le Stockage

### Volume de données

**Par run** (3 packages × 25 heures) :
- SP1 + HP1 + HP3 = ~1-4 GB

**Par jour** (16 runs) :
- 16 runs × 3 GB moyen = **~48 GB/jour**

**Stratégie de conservation recommandée** :
- Garder les 3 derniers runs (~12 GB)
- Archiver ou supprimer les runs plus anciens
- Prioriser les runs de 06Z, 09Z, 12Z, 15Z, 18Z pour archivage long terme

## Tests

### Tester la disponibilité d'un run horaire

```bash
# Test run de 14Z
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-09 14"

# Télécharger un fichier de test
sbt "arome/runMain org.soaringmeteo.arome.TestDownload 2025-11-09 14 --download"
```

### Vérifier les URLs générées

```scala
import org.soaringmeteo.arome.in.AromeRun

// Créer run 14Z
val run = AromeRun(2025, 11, 9, 14)

// Afficher URLs
println(run.gribUrl("SP1", 0))
println(run.gribUrl("HP1", 0))
println(run.gribUrl("HP3", 0))
```

## Limitations et Notes

### Fenêtre de disponibilité
- Runs disponibles : **06Z à 21Z uniquement**
- Pas de runs entre 22Z et 05Z (période nocturne)
- Pour données nocturnes : utiliser run 21Z avec forecast hours avancés

### Délai de publication
- Les fichiers GRIB2 sont généralement disponibles **1-2 heures** après l'heure du run
- Exemple : run 14Z disponible vers 15h-16h UTC

### Validation requise
⚠️ **Vérifier avec data.gouv.fr** que les runs horaires 06Z-21Z sont effectivement disponibles
- La documentation indique traditionnellement 8 runs/jour (toutes les 3h)
- Cette configuration suppose que Météo-France propose bien 16 runs horaires
- **Tester avec des URLs réelles** avant mise en production

## Migration depuis Runs Toutes les 3h

Si vous aviez configuré les anciens runs (00Z, 03Z, 06Z, 09Z, 12Z, 15Z, 18Z, 21Z) :

### Changements automatiques
- ✅ Validation mise à jour : accepte maintenant 06-21 (hourly)
- ✅ Run number : calculé automatiquement à partir de l'heure
- ✅ Auto-détection : trouve le run le plus récent dans la fenêtre 06Z-21Z

### Actions requises
- [ ] Vérifier que les URLs horaires existent sur data.gouv.fr
- [ ] Tester avec `TestDownload.scala` pour chaque heure (06-21)
- [ ] Ajuster la stratégie de stockage (16 runs/jour au lieu de 8)
- [ ] Mettre à jour les scripts de purge si nécessaires

## Exemples de Code

### Télécharger tous les runs de la journée

```scala
import org.soaringmeteo.arome.in.{AromeRun, AromeDownloader}
import scala.concurrent.Await
import scala.concurrent.duration._

val downloader = new AromeDownloader(60)
val baseDir = os.Path("/mnt/data/arome/grib")

// Pour chaque run horaire de 06Z à 21Z
for (hour <- 6 to 21) {
  val run = AromeRun(2025, 11, 9, hour)

  println(s"Downloading run ${hour}Z...")

  // Télécharger tous les packages pour H+0
  val files = Await.result(
    downloader.scheduleDownloadAllPackages(baseDir, run, hourOffset = 0),
    10.minutes
  )

  println(s"  Downloaded: ${files.keys.mkString(", ")}")
}
```

### Comparer plusieurs runs

```scala
// Créer 3 runs consécutifs
val run12 = AromeRun(2025, 11, 9, 12)
val run13 = AromeRun(2025, 11, 9, 13)
val run14 = AromeRun(2025, 11, 9, 14)

// Comparer les prévisions pour la même heure
// (run 12Z à H+2 vs run 13Z à H+1 vs run 14Z à H+0)
```

## Conclusion

La configuration horaire 06Z-21Z offre :
- ✅ 2x plus de mises à jour quotidiennes
- ✅ Délai max de 1h entre runs (au lieu de 3h)
- ✅ Couverture complète de la journée de vol
- ⚠️ Volume de stockage doublé (48 GB/jour au lieu de 24 GB)
- ⚠️ Nécessite validation que data.gouv.fr propose bien ces runs horaires
