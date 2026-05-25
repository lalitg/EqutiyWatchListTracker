#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
#  build.sh — build main-api-service fat JAR
#
#  Run from the repo root:
#    chmod +x main-api-service/build.sh
#    ./main-api-service/build.sh
#
#  What it does:
#    1. Installs user-service, auth-service, watchlist-service as plain library
#       JARs into the local Maven repository (~/.m2/).
#       The <classifier>exec</classifier> in each service's pom.xml means:
#         *-exec.jar  = runnable fat JAR  (standalone use)
#         *.jar       = plain library JAR (installed, used by main-api-service)
#    2. Builds main-api-service fat JAR: main-api-service/target/main-api-service-1.0.0.jar
#
#  Run the combined JAR:
#    java -jar main-api-service/target/main-api-service-1.0.0.jar
#
#  EC2 environment variables:
#    DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
#    JWT_SECRET, INTERNAL_API_KEY
#    APP_LOG_DIR (e.g. /var/log/main-api-service)
#    CORS_ALLOWED_ORIGINS
# ═══════════════════════════════════════════════════════════════════════════════

set -e  # exit immediately on any error

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "═══════════════════════════════════════════════"
echo " Building main-api-service"
echo " Repo root: $REPO_ROOT"
echo "═══════════════════════════════════════════════"

# ── Step 1: Install user-service plain JAR ───────────────────────────────────
echo ""
echo "▶ [1/4] Installing user-service library JAR..."
cd "$REPO_ROOT/user-service"
mvn install -DskipTests -q
echo "  ✓ user-service installed"

# ── Step 2: Install auth-service plain JAR ───────────────────────────────────
echo ""
echo "▶ [2/4] Installing auth-service library JAR..."
cd "$REPO_ROOT/auth-service"
mvn install -DskipTests -q
echo "  ✓ auth-service installed"

# ── Step 3: Install watchlist-service plain JAR ──────────────────────────────
echo ""
echo "▶ [3/4] Installing watchlist-service library JAR..."
cd "$REPO_ROOT/watchlist-service"
mvn install -DskipTests -q
echo "  ✓ watchlist-service installed"

# ── Step 4: Build main-api-service fat JAR ───────────────────────────────────
echo ""
echo "▶ [4/4] Packaging main-api-service..."
cd "$REPO_ROOT/main-api-service"
mvn package -DskipTests -q
echo "  ✓ main-api-service packaged"

echo ""
echo "═══════════════════════════════════════════════"
echo " Build complete!"
echo " JAR: main-api-service/target/main-api-service-1.0.0.jar"
echo ""
echo " Run:"
echo "   java -jar $REPO_ROOT/main-api-service/target/main-api-service-1.0.0.jar"
echo "═══════════════════════════════════════════════"
