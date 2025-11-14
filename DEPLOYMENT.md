# Déploiement du Frontend SoaringMeteo

Ce document décrit comment déployer le frontend SoaringMeteo vers le serveur de production.

## Prérequis

- Node.js 18+ installé
- Accès SSH au serveur de destination
- Clés SSH configurées pour l'authentification

## Configuration

Le frontend est configuré pour fonctionner sous le chemin `/v2/` :
- **Base URL**: `/v2/`
- **Assets**: `/v2/assets/`
- **Données API**: `/v2/data/`

Cette configuration est définie dans `frontend/vite.config.ts` :

```typescript
export default defineConfig(() => ({
  base: '/v2/',
  // ...
}));
```

## Build Local

### 1. Installation des dépendances

```bash
cd frontend
npm ci
```

### 2. Construction du frontend

```bash
npm run build
```

Cette commande génère les fichiers optimisés dans le dossier `dist/` :
- HTML minifié
- JavaScript bundlé et code-splitté (OpenLayers et SolidJS séparés)
- CSS optimisé
- Service Worker PWA
- Manifest pour l'application web progressive

### 3. Vérification du build

```bash
ls -la dist/
```

Vous devriez voir :
- `index.html` - Point d'entrée principal
- `assets/` - JavaScript et CSS bundlés
- `manifest.webmanifest` - Manifest PWA
- `sw.js` - Service Worker
- `favicon.*.png` - Icônes de l'application
- `robots.txt`

## Déploiement vers 51.254.207.208

### Option 1 : Déploiement avec rsync (recommandé)

Utilisez le script npm avec la variable d'environnement `SERVER` :

```bash
cd frontend
SERVER=51.254.207.208 npm run deploy
```

Cette commande :
1. Compile les traductions i18n
2. Construit le frontend pour production
3. Copie les fichiers via rsync vers `/home/soaringmeteo.org/v2/`

**Note** : Le dossier `data/` est exclu du rsync car il contient les prévisions météo générées par le backend.

### Option 2 : Déploiement manuel avec rsync

```bash
cd frontend
npm run build
rsync --recursive --times --delete --exclude=data/ dist/ root@51.254.207.208:/home/soaringmeteo.org/v2/
```

### Option 3 : Déploiement manuel avec scp

```bash
cd frontend
npm run build
ssh root@51.254.207.208 "mkdir -p /home/soaringmeteo.org/v2"
scp -r dist/* root@51.254.207.208:/home/soaringmeteo.org/v2/
```

## Configuration du Serveur Web

Le serveur web (Apache/Nginx) doit être configuré pour :

1. Servir les fichiers statiques depuis `/home/soaringmeteo.org/v2/`
2. Pointer l'URL `http://51.254.207.208/v2/` vers ce répertoire
3. Servir les données météo depuis `/home/soaringmeteo.org/v2/data/`

### Exemple de configuration Nginx

```nginx
server {
    listen 80;
    server_name 51.254.207.208;

    location /v2/ {
        alias /home/soaringmeteo.org/v2/;
        try_files $uri $uri/ /v2/index.html;

        # Cache des assets
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }

        # Pas de cache pour index.html
        location = /v2/index.html {
            add_header Cache-Control "no-cache, no-store, must-revalidate";
        }
    }
}
```

### Exemple de configuration Apache

```apache
<VirtualHost *:80>
    ServerName 51.254.207.208

    Alias /v2 /home/soaringmeteo.org/v2

    <Directory /home/soaringmeteo.org/v2>
        Options -Indexes +FollowSymLinks
        AllowOverride None
        Require all granted

        # Réécriture pour SPA
        <IfModule mod_rewrite.c>
            RewriteEngine On
            RewriteBase /v2/
            RewriteRule ^index\.html$ - [L]
            RewriteCond %{REQUEST_FILENAME} !-f
            RewriteCond %{REQUEST_FILENAME} !-d
            RewriteRule . /v2/index.html [L]
        </IfModule>

        # Cache des assets
        <FilesMatch "\.(js|css|png|jpg|jpeg|gif|ico|svg)$">
            Header set Cache-Control "max-age=31536000, public, immutable"
        </FilesMatch>

        # Pas de cache pour index.html
        <FilesMatch "index\.html$">
            Header set Cache-Control "no-cache, no-store, must-revalidate"
        </FilesMatch>
    </Directory>
</VirtualHost>
```

## Déploiement des Données Météo

Le frontend nécessite que les données météo soient disponibles dans `/home/soaringmeteo.org/v2/data/`. Ces données sont générées par le backend (Scala).

Structure attendue :
```
/home/soaringmeteo.org/v2/data/
└── 7/                           # Version du format de données
    ├── gfs/                     # Modèle GFS
    │   ├── forecast.json        # Métadonnées des prévisions
    │   └── 2024-11-14T06/       # Run de prévision
    │       └── zone/            # Zone géographique
    │           ├── thermal/     # Couche thermique
    │           │   └── 12.png   # Heure +12
    │           └── wind/        # Couche vent
    │               └── 12/      # Tuiles vectorielles MVT
    ├── wrf/                     # Modèle WRF
    │   └── ...
    └── arome/                   # Modèle AROME
        └── ...
```

## Vérification du Déploiement

1. **Vérifier que les fichiers sont copiés** :
   ```bash
   ssh root@51.254.207.208 "ls -la /home/soaringmeteo.org/v2/"
   ```

2. **Tester l'URL dans le navigateur** :
   ```
   http://51.254.207.208/v2/
   ```

3. **Vérifier la console du navigateur** pour les erreurs

4. **Tester le Service Worker PWA** :
   - Ouvrir les DevTools
   - Aller dans Application > Service Workers
   - Vérifier que le SW est enregistré

5. **Vérifier que les données météo se chargent** :
   - Ouvrir Network dans DevTools
   - Vérifier les requêtes vers `/v2/data/7/*/forecast.json`

## Troubleshooting

### Erreur 404 sur les assets

**Problème** : Les fichiers CSS/JS ne se chargent pas

**Solution** : Vérifier que le serveur web sert bien le répertoire `/home/soaringmeteo.org/v2/`

### Erreur 404 sur /v2/data/

**Problème** : Les données météo ne se chargent pas

**Solution** :
1. Vérifier que le backend a généré les données dans `/home/soaringmeteo.org/v2/data/`
2. Vérifier les permissions du répertoire

### Page blanche

**Problème** : L'application ne s'affiche pas

**Solution** :
1. Ouvrir la console du navigateur pour voir les erreurs JavaScript
2. Vérifier que `index.html` se charge correctement
3. Vérifier les chemins des assets dans `index.html`

### Service Worker ne se met pas à jour

**Problème** : Les modifications ne sont pas visibles après déploiement

**Solution** :
1. Vider le cache du navigateur
2. Désinscrire le Service Worker dans DevTools > Application > Service Workers
3. Recharger la page

## Notes Importantes

1. **Exclusion de data/** : Le script de déploiement exclut automatiquement le dossier `data/` pour éviter d'écraser les prévisions météo générées par le backend.

2. **Version du format de données** : Le frontend utilise la version 7 du format de données (`data/7/`). Si le backend génère une version différente, mettre à jour `frontend/src/data/ForecastMetadata.ts`.

3. **Multi-langues** : Le frontend supporte 8 langues (EN, FR, DE, IT, ES, PT, PL, SK). Les traductions sont compilées dans le build.

4. **PWA** : Le frontend est une Progressive Web App. Le Service Worker permet le fonctionnement offline après le premier chargement.

## Développement Local

Pour tester le frontend en local :

```bash
cd frontend
npm start
```

L'application sera accessible sur `http://localhost:3000/v2/`

**Note** : En développement, les données sont servies depuis `../backend/target/forecast/data/`. Le backend doit avoir généré des données de test avant de démarrer le serveur de développement.

## Ressources

- **Documentation SolidJS** : https://www.solidjs.com/
- **Documentation Vite** : https://vitejs.dev/
- **Documentation OpenLayers** : https://openlayers.org/
- **Guide PWA** : https://vite-pwa-org.netlify.app/

## Support

Pour toute question ou problème :
- Email : equipe@soaringmeteo.org
- GitHub Issues : https://github.com/numix74/HaizeHegoa/issues
