# AROME Pipeline - Vérification du Déploiement

## 📋 Résumé du Problème Identifié

La pipeline AROME écrivait les données dans une structure de répertoires incorrecte qui ne correspondait pas aux attentes du frontend. Les données n'étaient pas accessibles à l'URL `http://51.254.207.208/data/7/arome/`.

### ❌ Structure Incorrecte (Avant)
```
/mnt/soaringmeteo-data/arome/output/pays_basque/
└── maps/
    ├── 00/
    │   ├── thermal-velocity.png
    │   └── ...
    └── ...
```

### ✅ Structure Correcte (Maintenant)
```
/mnt/soaringmeteo-data/arome/output/7/arome/
├── forecast.json
└── 2025-11-11T06/
    └── pays-basque/
        ├── thermal-velocity/
        │   ├── 0.png
        │   ├── 1.png
        │   └── ...24.png
        ├── wind-barbs/
        │   └── 0/{z}-{x}-{y}.mvt
        └── locations/
            └── {x}-{y}.json
```

---

## 🔧 Corrections Apportées

### 1. Configuration (`reference.conf`)
- ✅ Changé de `output-directory` par zone à `output-base-directory` global
- ✅ Structure versionnée automatique (`/7/arome/`)

### 2. Structure de Code
- ✅ Créé `out/package.scala` avec fonctions de chemins (comme GFS)
- ✅ Modifié `Main.scala` pour utiliser la structure versionnée
- ✅ Modifié `Settings.scala` pour lire la nouvelle config

### 3. Génération JSON
- ✅ `forecast.json` généré dans `/path/7/arome/`
- ✅ Fichiers de localisation dans structure correcte

---

## ✓ Checklist de Déploiement sur VPS

### Étape 1 : Mettre à Jour la Configuration

Sur le VPS `51.254.207.208`, modifier le fichier de config AROME :

```bash
ssh user@51.254.207.208
cd /home/ubuntu/soaringmeteo/backend
nano pays_basque.conf
```

**Nouvelle structure du fichier de config :**
```hocon
arome {
  # Nouveau : base directory au lieu de per-zone
  output-base-directory = "/mnt/soaringmeteo-data/arome/output"

  zones = [
    {
      name = "Pays Basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      # SUPPRIMÉ : output-directory (maintenant calculé automatiquement)
    }
  ]
}

h2db {
  url = "jdbc:h2:file:/mnt/soaringmeteo-data/arome/arome.h2"
  driver = "org.h2.Driver"
}
```

### Étape 2 : Vérifier la Configuration Nginx

```bash
# Vérifier la config nginx actuelle
sudo nginx -T | grep -A 20 "server_name.*51.254.207.208"

# Éditer si nécessaire
sudo nano /etc/nginx/sites-available/soaringmeteo
```

**Configuration Nginx Requise :**
```nginx
server {
    listen 80;
    server_name 51.254.207.208;

    root /var/www/soaringmeteo;

    # Serve frontend static files
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Serve AROME data (CRITIQUE)
    location /data/7/arome/ {
        alias /mnt/soaringmeteo-data/arome/output/7/arome/;
        add_header Access-Control-Allow-Origin *;
        add_header Cache-Control "public, max-age=3600";
        autoindex off;
    }

    # Serve GFS data (référence)
    location /data/7/gfs/ {
        alias /mnt/soaringmeteo-data/gfs/output/7/gfs/;
        add_header Access-Control-Allow-Origin *;
        add_header Cache-Control "public, max-age=3600";
    }

    # Serve MVT tiles with correct content-type
    location ~ \.mvt$ {
        add_header Content-Type "application/vnd.mapbox-vector-tile";
        add_header Access-Control-Allow-Origin *;
    }

    # Serve JSON with correct content-type
    location ~ \.json$ {
        add_header Content-Type "application/json";
        add_header Access-Control-Allow-Origin *;
    }
}
```

**Appliquer la config :**
```bash
sudo nginx -t              # Tester la config
sudo systemctl reload nginx # Recharger nginx
```

### Étape 3 : Recompiler et Redéployer le Code

```bash
cd /home/ubuntu/soaringmeteo/backend

# Pull les dernières modifications
git pull origin claude/implement-arome-adapter-011CV2iLPUqhzwKWsj1poAFG

# Recompiler
sbt "project arome" compile

# Test manuel (optionnel)
sbt "project arome" "run pays_basque.conf"
```

### Étape 4 : Exécuter la Pipeline et Vérifier les Sorties

```bash
# Exécuter la pipeline
cd /home/ubuntu/soaringmeteo/backend/scripts
./arome_daily_pipeline.sh

# Vérifier les logs
tail -100 /var/log/soaringmeteo/arome_$(date +%Y%m%d).log

# Vérifier la structure des fichiers
ls -lah /mnt/soaringmeteo-data/arome/output/7/arome/
```

**Résultat attendu :**
```
/mnt/soaringmeteo-data/arome/output/7/arome/
├── forecast.json           <- DOIT EXISTER
├── 2025-11-11T06/         <- Date du run
│   └── pays-basque/
│       ├── thermal-velocity/
│       │   ├── 0.png
│       │   ├── 1.png
│       │   └── ...
│       ├── boundary-layer-depth/
│       ├── xc-potential/
│       ├── wind-barbs/
│       │   ├── 0/
│       │   └── ...
│       └── locations/
│           ├── 0-0.json
│           └── ...
└── 2025-11-10T06/         <- Run précédent
    └── ...
```

### Étape 5 : Tester l'Accès Web

```bash
# Test 1 : forecast.json (CRITIQUE)
curl -I http://51.254.207.208/data/7/arome/forecast.json

# Doit retourner : HTTP/1.1 200 OK
# Doit avoir : Content-Type: application/json

# Test 2 : Contenu de forecast.json
curl http://51.254.207.208/data/7/arome/forecast.json | jq .

# Doit afficher les zones et forecasts

# Test 3 : PNG map
RUN_DATE=$(curl -s http://51.254.207.208/data/7/arome/forecast.json | jq -r '.forecasts[0].path')
curl -I "http://51.254.207.208/data/7/arome/${RUN_DATE}/pays-basque/thermal-velocity/12.png"

# Doit retourner : HTTP/1.1 200 OK
# Doit avoir : Content-Type: image/png

# Test 4 : Location JSON
curl -I "http://51.254.207.208/data/7/arome/${RUN_DATE}/pays-basque/locations/5-10.json"

# Doit retourner : HTTP/1.1 200 OK

# Test 5 : MVT tiles
curl -I "http://51.254.207.208/data/7/arome/${RUN_DATE}/pays-basque/wind-barbs/12/8-128-90.mvt"

# Doit retourner : HTTP/1.1 200 OK
# Doit avoir : Content-Type: application/vnd.mapbox-vector-tile
```

---

## 🧪 Tests Frontend

### Test dans le Navigateur

1. **Ouvrir l'application :**
   ```
   http://51.254.207.208/
   ```

2. **Sélectionner AROME :**
   - Menu des modèles → "AROME Pays Basque (2.5 km)"

3. **Vérifier les couches :**
   - ✓ Thermal Velocity (carte PNG)
   - ✓ Boundary Layer Depth
   - ✓ XC Potential
   - ✓ Wind Barbs (tuiles vectorielles)

4. **Vérifier les méteogrammes :**
   - Cliquer sur une position
   - Le panneau de droite doit afficher les prévisions
   - Graphiques de vent, thermiques, etc.

### Console Développeur (F12)

Vérifier qu'il n'y a **pas d'erreurs** de type :
```
❌ Failed to load resource: net::ERR_FILE_NOT_FOUND
   http://51.254.207.208/data/7/arome/forecast.json

❌ 404 Not Found
   http://51.254.207.208/data/7/arome/2025-11-11T06/pays-basque/thermal-velocity/12.png
```

Si ces erreurs apparaissent → **Problème de configuration nginx**

---

## 🚨 Dépannage

### Erreur : `forecast.json` retourne 404

**Cause :** Nginx ne sert pas le bon répertoire

**Solution :**
```bash
# Vérifier que le fichier existe
ls -la /mnt/soaringmeteo-data/arome/output/7/arome/forecast.json

# Vérifier la config nginx
sudo nginx -T | grep -A 5 "location /data/7/arome"

# Vérifier les permissions
sudo chmod 755 /mnt/soaringmeteo-data/arome/output/7/arome
sudo chmod 644 /mnt/soaringmeteo-data/arome/output/7/arome/forecast.json
```

### Erreur : PNG retournent 404

**Cause :** Structure de répertoires incorrecte

**Solution :**
```bash
# Vérifier la structure
find /mnt/soaringmeteo-data/arome/output/7/arome -name "*.png" | head -5

# Doit afficher des chemins comme :
# .../7/arome/2025-11-11T06/pays-basque/thermal-velocity/0.png

# Si les PNG sont dans maps/00/ → relancer la pipeline avec le code corrigé
```

### Erreur : Pas de données dans le frontend

**Cause 1 :** forecast.json vide ou invalide

```bash
# Vérifier le contenu
cat /mnt/soaringmeteo-data/arome/output/7/arome/forecast.json | jq .

# Doit avoir :
# - "zones": [...]
# - "forecasts": [...]
```

**Cause 2 :** Base de données vide

```bash
# Vérifier la base H2
ls -lh /mnt/soaringmeteo-data/arome/arome.h2.db

# Si fichier petit (<100KB) → pas de données, relancer la pipeline
```

---

## 📊 Monitoring de Production

### Vérifier les Cron Jobs

```bash
crontab -l | grep arome

# Doit afficher :
# 0 10 * * * /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh
```

### Vérifier les Logs

```bash
# Log du jour
tail -f /var/log/soaringmeteo/arome_$(date +%Y%m%d).log

# Logs récents
ls -lth /var/log/soaringmeteo/arome_*.log | head -5

# Rechercher des erreurs
grep -i error /var/log/soaringmeteo/arome_*.log
```

### Vérifier l'Espace Disque

```bash
df -h /mnt/soaringmeteo-data

# AROME génère ~500MB par run avec historique de 5 runs
du -sh /mnt/soaringmeteo-data/arome/output/7/arome/*
```

---

## 🎯 Critères de Succès

✅ **Pipeline complète fonctionnelle si :**

1. ✓ `forecast.json` accessible à `http://51.254.207.208/data/7/arome/forecast.json`
2. ✓ PNG maps accessibles à `http://51.254.207.208/data/7/arome/{run}/pays-basque/{variable}/{hour}.png`
3. ✓ MVT tiles accessibles
4. ✓ Location JSON accessibles
5. ✓ Frontend charge les données AROME
6. ✓ Cartes s'affichent correctement
7. ✓ Méteogrammes fonctionnent
8. ✓ Pas d'erreurs 404 dans console

---

## 📞 Support

Si problèmes persistent :

1. **Vérifier les logs complets :**
   ```bash
   cat /var/log/soaringmeteo/arome_$(date +%Y%m%d).log
   ```

2. **Vérifier la structure générée :**
   ```bash
   tree -L 4 /mnt/soaringmeteo-data/arome/output/7/arome/
   ```

3. **Tester en local :**
   ```bash
   cd /home/ubuntu/soaringmeteo/backend
   sbt "project arome" "run pays_basque.conf"
   ```

---

**Date de dernière mise à jour :** 2025-11-11
**Version de la pipeline :** 7 (format version)
**Commits :** `991ae45`, `668e75f`
