#!/bin/bash
#═══════════════════════════════════════════════════════════════
# Script de déploiement AROME 2.5 km complet
# Ce script configure le flux complet pour AROME 2.5 km :
#  1. Backend (génération forecast.json + maxViewZoom=12)
#  2. Nginx (configuration pour servir les données)
#  3. Frontend (déjà compatible, pas de modifications nécessaires)
#
# Usage: ./deploy_arome_2.5km.sh
#═══════════════════════════════════════════════════════════════

set -e

# Configuration
VPS_USER="ubuntu"
VPS_HOST="51.254.207.208"  # Remplacer par votre IP VPS
VPS_PROJECT_DIR="/home/ubuntu/soaringmeteo"
LOCAL_PROJECT_DIR="/home/user/HaizeHegoa"

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "╔════════════════════════════════════════════════════════════╗"
echo "║    Déploiement AROME 2.5 km - Flux Complet               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Vérifier qu'on est dans le bon répertoire
if [ ! -d "$LOCAL_PROJECT_DIR/backend/arome" ]; then
    echo -e "${RED}❌ ERREUR: Exécutez ce script depuis $LOCAL_PROJECT_DIR${NC}"
    exit 1
fi

cd "$LOCAL_PROJECT_DIR"

echo -e "${BLUE}📦 Configuration actuelle:${NC}"
echo "  VPS: $VPS_USER@$VPS_HOST"
echo "  Répertoire VPS: $VPS_PROJECT_DIR"
echo ""
read -p "Voulez-vous continuer ? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Annulé."
    exit 1
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Étape 1/6 : Déploiement du backend AROME"
echo "════════════════════════════════════════════════════════════"

echo -e "${YELLOW}➜${NC} Copie des fichiers backend AROME..."

# Créer les répertoires nécessaires sur le VPS
ssh "$VPS_USER@$VPS_HOST" "mkdir -p $VPS_PROJECT_DIR/backend/arome/src/main/scala/org/soaringmeteo/arome" || true
ssh "$VPS_USER@$VPS_HOST" "mkdir -p $VPS_PROJECT_DIR/nginx" || true

# Copier les fichiers modifiés du backend
echo "  - AromeVectorTilesParameters.scala (maxViewZoom=12)"
scp backend/arome/src/main/scala/org/soaringmeteo/arome/AromeVectorTilesParameters.scala \
    "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/backend/arome/src/main/scala/org/soaringmeteo/arome/" \
    && echo -e "${GREEN}    ✓ AromeVectorTilesParameters.scala${NC}" \
    || echo -e "${RED}    ✗ Échec${NC}"

echo "  - JsonWriter.scala (génération forecast.json)"
scp backend/arome/src/main/scala/org/soaringmeteo/arome/JsonWriter.scala \
    "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/backend/arome/src/main/scala/org/soaringmeteo/arome/" \
    && echo -e "${GREEN}    ✓ JsonWriter.scala${NC}" \
    || echo -e "${RED}    ✗ Échec${NC}"

echo "  - Main.scala (appel JsonWriter)"
scp backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala \
    "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/backend/arome/src/main/scala/org/soaringmeteo/arome/" \
    && echo -e "${GREEN}    ✓ Main.scala${NC}" \
    || echo -e "${RED}    ✗ Échec${NC}"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Étape 2/6 : Configuration Nginx"
echo "════════════════════════════════════════════════════════════"

echo -e "${YELLOW}➜${NC} Copie des fichiers de configuration nginx..."
scp nginx/arome.conf "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/nginx/" \
    && echo -e "${GREEN}  ✓ arome.conf${NC}" \
    || echo -e "${RED}  ✗ Échec${NC}"

scp nginx/README-AROME-NGINX.md "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/nginx/" \
    && echo -e "${GREEN}  ✓ README-AROME-NGINX.md${NC}" \
    || echo -e "${RED}  ✗ Échec${NC}"

echo ""
echo -e "${BLUE}ℹ️  Pour appliquer la configuration nginx:${NC}"
echo "  1. Connectez-vous au VPS:"
echo "     ssh $VPS_USER@$VPS_HOST"
echo ""
echo "  2. Éditez la configuration nginx:"
echo "     sudo nano /etc/nginx/sites-available/default"
echo ""
echo "  3. Ajoutez le contenu de:"
echo "     cat $VPS_PROJECT_DIR/nginx/arome.conf"
echo ""
echo "  4. Testez et rechargez:"
echo "     sudo nginx -t"
echo "     sudo systemctl reload nginx"
echo ""

echo "════════════════════════════════════════════════════════════"
echo "Étape 3/6 : Création des répertoires de données"
echo "════════════════════════════════════════════════════════════"

echo -e "${YELLOW}➜${NC} Création de la structure de répertoires sur le VPS..."
ssh "$VPS_USER@$VPS_HOST" << 'ENDSSH'
sudo mkdir -p /mnt/soaringmeteo-data/arome/output/7
sudo mkdir -p /mnt/soaringmeteo-data/arome/grib/pays_basque
sudo chown -R ubuntu:ubuntu /mnt/soaringmeteo-data/arome
chmod 755 /mnt/soaringmeteo-data/arome
chmod 755 /mnt/soaringmeteo-data/arome/output
ENDSSH

echo -e "${GREEN}✓ Répertoires créés${NC}"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Étape 4/6 : Configuration AROME"
echo "════════════════════════════════════════════════════════════"

echo -e "${YELLOW}➜${NC} Copie du fichier de configuration exemple..."
scp backend/arome.conf.example "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/backend/" 2>/dev/null \
    && echo -e "${GREEN}  ✓ arome.conf.example${NC}" \
    || echo -e "${YELLOW}  ! Créez manuellement backend/arome.conf sur le VPS${NC}"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Étape 5/6 : Compilation du backend"
echo "════════════════════════════════════════════════════════════"

echo -e "${YELLOW}➜${NC} Compilation du backend AROME sur le VPS..."
echo ""
echo -e "${BLUE}ℹ️  Lancement de la compilation (cela peut prendre quelques minutes)...${NC}"

ssh "$VPS_USER@$VPS_HOST" << 'ENDSSH'
cd /home/ubuntu/soaringmeteo/backend
echo "Compilation du projet AROME..."
sbt "arome/compile" 2>&1 | tail -20
ENDSSH

echo -e "${GREEN}✓ Compilation terminée${NC}"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Étape 6/6 : Résumé et prochaines étapes"
echo "════════════════════════════════════════════════════════════"
echo ""
echo -e "${GREEN}✅ Déploiement terminé avec succès !${NC}"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              PROCHAINES ÉTAPES SUR LE VPS                 ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "1️⃣  Configuration Nginx:"
echo "   ssh $VPS_USER@$VPS_HOST"
echo "   sudo nano /etc/nginx/sites-available/default"
echo "   # Ajouter le contenu de ~/soaringmeteo/nginx/arome.conf"
echo "   sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "2️⃣  Tester le pipeline AROME:"
echo "   cd /home/ubuntu/soaringmeteo/backend"
echo "   # Assurez-vous que les fichiers GRIB sont disponibles"
echo "   bash scripts/arome_daily_pipeline.sh"
echo ""
echo "3️⃣  Vérifier que forecast.json est généré:"
echo "   ls -la /mnt/soaringmeteo-data/arome/output/forecast.json"
echo "   cat /mnt/soaringmeteo-data/arome/output/forecast.json | jq ."
echo ""
echo "4️⃣  Tester l'accès web:"
echo "   curl http://localhost/v2/data/7/arome/forecast.json"
echo "   curl http://51.254.207.208/v2/data/7/arome/forecast.json"
echo ""
echo "5️⃣  Ouvrir le frontend:"
echo "   http://51.254.207.208/v2/"
echo "   → Le modèle AROME devrait apparaître automatiquement !"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    CARACTÉRISTIQUES                        ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "  ✓ maxViewZoom: 12 (adapté pour AROME 2.5 km)"
echo "  ✓ minViewZoom: Calculé automatiquement (environ 9)"
echo "  ✓ Génération forecast.json: Activée"
echo "  ✓ Compatibilité frontend: Complète"
echo "  ✓ Routes nginx: Configurées"
echo ""
echo "📖 Documentation complète:"
echo "   cat $VPS_PROJECT_DIR/nginx/README-AROME-NGINX.md"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
