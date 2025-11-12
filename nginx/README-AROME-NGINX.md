# Configuration Nginx pour AROME 2.5 km

## Vue d'ensemble

Ce guide explique comment configurer nginx pour servir les données AROME 2.5 km sur le VPS.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ Frontend (http://51.254.207.208/v2/)                    │
│ - Charge dynamiquement GFS, WRF et AROME               │
│ - Lit /v2/data/7/arome/forecast.json                  │
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│ Nginx (Configuration)                                    │
│ /v2/data/7/arome/* → /mnt/soaringmeteo-data/arome/output/│
└─────────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│ Données AROME                                            │
│ /mnt/soaringmeteo-data/arome/output/                   │
│   ├── forecast.json                                     │
│   └── 2025-11-12T06/                                   │
│       └── pays_basque/                                  │
│           └── maps/                                     │
│               ├── 00/ (PNG + MVT)                       │
│               ├── 01/ (PNG + MVT)                       │
│               └── ...                                   │
└─────────────────────────────────────────────────────────┘
```

## Étapes d'installation

### 1. Copier le fichier de configuration

Sur le VPS :

```bash
# Copier le fichier de configuration
sudo cp /home/ubuntu/HaizeHegoa/nginx/arome.conf /etc/nginx/sites-available/

# Ou si vous intégrez directement dans la config existante :
sudo nano /etc/nginx/sites-available/default
```

### 2. Intégrer la configuration AROME

Ajoutez le contenu de `arome.conf` dans le bloc `server` existant qui sert sur `51.254.207.208`.

Votre configuration devrait ressembler à :

```nginx
server {
    listen 80;
    server_name 51.254.207.208 _;

    # Frontend GFS (existant)
    location /v2/ {
        alias /home/ubuntu/soaringmeteo/frontend/dist/;
        try_files $uri $uri/ /v2/index.html;
        index index.html;
    }

    # Données statiques GFS (existant)
    location ~ ^/v2/data/7/gfs/(.*)$ {
        alias /mnt/soaringmeteo-data/gfs/output/$1;
        expires 1h;
    }

    # Pas de cache pour forecast.json GFS (existant)
    location ~ /v2/data/7/gfs/forecast.json$ {
        alias /mnt/soaringmeteo-data/gfs/output/forecast.json;
        expires -1;
        add_header Cache-Control "no-store, no-cache, must-revalidate";
    }

    # ========================================
    # AROME Configuration (NOUVEAU)
    # ========================================

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
}
```

### 3. Créer la structure de répertoires

```bash
sudo mkdir -p /mnt/soaringmeteo-data/arome/output
sudo chown -R ubuntu:ubuntu /mnt/soaringmeteo-data/arome
```

### 4. Tester la configuration nginx

```bash
# Vérifier la syntaxe
sudo nginx -t

# Si OK, recharger nginx
sudo systemctl reload nginx
```

### 5. Configurer le backend AROME

Créer un fichier de configuration pour AROME (exemple `/home/ubuntu/soaringmeteo/backend/arome.conf`) :

```hocon
arome {
  zones = [
    {
      name = "pays_basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      output-directory = "/mnt/soaringmeteo-data/arome/output/7"
    }
  ]
}
```

### 6. Tester le pipeline AROME

```bash
cd /home/ubuntu/soaringmeteo/backend
sbt "arome/run /home/ubuntu/soaringmeteo/backend/arome.conf"
```

### 7. Vérifier que les fichiers sont accessibles

```bash
# Vérifier forecast.json
curl http://localhost/v2/data/7/arome/forecast.json

# Vérifier une carte PNG (exemple)
ls -la /mnt/soaringmeteo-data/arome/output/*/pays_basque/maps/00/*.png

# Tester l'accès web
curl -I http://51.254.207.208/v2/data/7/arome/forecast.json
```

## Dépannage

### Erreur 404 sur forecast.json

```bash
# Vérifier que le fichier existe
ls -la /mnt/soaringmeteo-data/arome/output/forecast.json

# Vérifier les permissions
sudo chmod 644 /mnt/soaringmeteo-data/arome/output/forecast.json
```

### Erreur 403 Forbidden

```bash
# Vérifier les permissions du répertoire
sudo chmod 755 /mnt/soaringmeteo-data/arome
sudo chmod 755 /mnt/soaringmeteo-data/arome/output

# Vérifier l'utilisateur nginx
ps aux | grep nginx

# Si nginx tourne sous www-data, ajuster les permissions
sudo chown -R www-data:www-data /mnt/soaringmeteo-data/arome/output
```

### Le frontend ne charge pas AROME

1. Ouvrir la console du navigateur (F12)
2. Vérifier les erreurs réseau
3. Vérifier que forecast.json est bien chargé :
   ```
   Network → XHR → v2/data/7/arome/forecast.json
   ```

4. Vérifier le contenu de forecast.json :
   ```json
   {
     "zones": [
       {
         "id": "pays_basque",
         "label": "Pays_basque",
         "raster": {
           "proj": "EPSG:4326",
           "resolution": 0.025,
           "extent": [...]
         },
         "vectorTiles": {
           "extent": [...],
           "zoomLevels": ...,
           "minZoom": 9,
           "tileSize": 512
         }
       }
     ],
     "forecasts": [...]
   }
   ```

## URLs d'accès

Une fois configuré, AROME sera accessible via :

- **forecast.json**: `http://51.254.207.208/v2/data/7/arome/forecast.json`
- **Cartes PNG**: `http://51.254.207.208/v2/data/7/arome/2025-11-12T06/pays_basque/maps/00/thermal-velocity.png`
- **Tuiles vectorielles**: `http://51.254.207.208/v2/data/7/arome/2025-11-12T06/pays_basque/maps/00/wind-surface/0-0-0.mvt`

## Logs

```bash
# Logs nginx
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log

# Logs AROME backend
tail -f /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
```

## Automatisation

Pour automatiser le pipeline AROME, ajoutez une tâche cron :

```bash
# Éditer crontab
crontab -e

# Ajouter (exemple : tous les jours à 7h UTC)
0 7 * * * /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh >> /home/ubuntu/soaringmeteo/backend/logs/arome_cron.log 2>&1
```
