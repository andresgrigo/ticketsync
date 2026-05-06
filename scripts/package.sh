#!/usr/bin/env bash
# TicketSync ? Script de empaquetado para Linux / macOS
# Genera una distribucion portable en dist/ (requiere Java 21 en el sistema destino).
# Con --installer genera ademas un instalador nativo (.deb / .dmg).
#
# USO:
#   ./scripts/package.sh              # dist/ con JARs + launcher
#   ./scripts/package.sh --installer  # dist/ + instalador nativo

set -euo pipefail

FX_VERSION="21.0.2"
MAIN_JAR="ticketsync-1.0-SNAPSHOT.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
OUT_DIR="$ROOT/dist"
LIB_DIR="$OUT_DIR/lib"
DEPS_DIR="$ROOT/target/deps"
INSTALLER=false

for arg in "$@"; do
    case $arg in
        --installer) INSTALLER=true ;;
        *) echo "Argumento desconocido: $arg"; exit 1 ;;
    esac
done

# Detectar plataforma para JARs JavaFX con native libs
case "$(uname -s)" in
    Linux*)  FX_PLATFORM="linux" ;;
    Darwin*) FX_PLATFORM="mac"   ;;
    *)       echo "Plataforma no soportada"; exit 1 ;;
esac

M2="$HOME/.m2/repository/org/openjfx"
FX_MODULES=(javafx-base javafx-controls javafx-fxml javafx-graphics)

echo "=== TicketSync Packaging ==="

# 1. Compilar y empaquetar
echo -e "\n[1/3] Compilando con Maven..."
cd "$ROOT"
mvn --batch-mode clean package -DskipTests

# Copiar deps runtime (excluir JavaFX ? JARs sin clasificador estan vacios)
mvn --batch-mode dependency:copy-dependencies -q \
    -DoutputDirectory="$DEPS_DIR" \
    -DincludeScope=runtime \
    -Dexcludes="org.openjfx:*"

# Copiar JARs de JavaFX especificos de la plataforma
for mod in "${FX_MODULES[@]}"; do
    src="$M2/$mod/$FX_VERSION/$mod-$FX_VERSION-$FX_PLATFORM.jar"
    if [ -f "$src" ]; then
        cp "$src" "$DEPS_DIR/"
    else
        echo "AVISO: $src no encontrado. Ejecuta 'mvn javafx:run' primero." >&2
    fi
done

# 2. Crear directorio de distribucion
echo -e "\n[2/3] Creando distribucion en $OUT_DIR..."
rm -rf "$OUT_DIR"
mkdir -p "$LIB_DIR"

cp "$ROOT/target/$MAIN_JAR" "$LIB_DIR/"
cp "$DEPS_DIR/"* "$LIB_DIR/"

# Crear launcher
cat > "$OUT_DIR/ticketsync.sh" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
if [ -z "${TICKETSYNC_MASTER_KEY:-}" ]; then
  echo "ERROR: Establece TICKETSYNC_MASTER_KEY antes de ejecutar TicketSync." >&2
  exit 1
fi
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java --module-path "$DIR/lib" --add-modules ALL-MODULE-PATH \
  -m com.ticketsync/com.ticketsync.App "$@"
LAUNCHER
chmod +x "$OUT_DIR/ticketsync.sh"

echo -e "\nDistribucion lista en: $OUT_DIR"
echo "Para ejecutar:"
echo "  1. export TICKETSYNC_MASTER_KEY=tu-clave"
echo "  2. $OUT_DIR/ticketsync.sh"
echo ""
echo "NOTA: El sistema destino necesita Java 21+ con JAVA_HOME configurado."

# 3. Instalador nativo (opcional)
if [ "$INSTALLER" = true ]; then
    echo -e "\n[3/3] Generando instalador nativo con jpackage..."
    case "$FX_PLATFORM" in
        linux) PKG_TYPE="deb" ;;
        mac)   PKG_TYPE="dmg" ;;
    esac
    INS_DIR="$OUT_DIR/installer"
    mkdir -p "$INS_DIR"

    jpackage \
        --type "$PKG_TYPE" \
        --name "TicketSync" \
        --app-version "1.0" \
        --input "$LIB_DIR" \
        --main-jar "$MAIN_JAR" \
        --main-class "com.ticketsync.App" \
        --java-options "--module-path \$APPDIR --add-modules ALL-MODULE-PATH" \
        --dest "$INS_DIR" \
        --description "Sistema de gestion de tickets"

    echo -e "\nInstalador: $INS_DIR"
fi

echo -e "\n=== Empaquetado completado ==="