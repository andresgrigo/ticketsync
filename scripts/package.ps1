# TicketSync — Script de empaquetado para Windows
# Genera una distribucion portable en dist/ (requiere Java 21 en el sistema destino).
#
# USO:
#   .\scripts\package.ps1              # dist/ con JARs + launcher
#   .\scripts\package.ps1 -Installer   # dist/ + instalador .exe

param([switch]$Installer)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Push-Location (Split-Path -Parent $PSScriptRoot)   # cd al directorio raiz del proyecto

try {

$MainJar = "ticketsync-1.0-SNAPSHOT.jar"
$DepsDir = "target\dependency"    # directorio de salida por defecto de dependency:copy-dependencies
$OutDir  = "dist"
$LibDir  = "dist\lib"

Write-Host "=== TicketSync Packaging ===" -ForegroundColor Cyan

# 1. Compilar y recopilar dependencias
Write-Host "`n[1/3] Compilando con Maven..." -ForegroundColor Yellow

mvn --batch-mode clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn package fallo (codigo $LASTEXITCODE)" }

mvn --batch-mode dependency:copy-dependencies -q -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) { throw "dependency:copy-dependencies fallo" }

$jarCount = (Get-ChildItem $DepsDir -Filter "*.jar").Count
Write-Host "  $jarCount dependencias recopiladas en $DepsDir"

# 2. Crear directorio de distribucion
Write-Host "`n[2/3] Creando distribucion..." -ForegroundColor Yellow

if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path $LibDir -Force | Out-Null

Copy-Item "target\$MainJar" $LibDir
Get-ChildItem $DepsDir -Filter "*.jar" | Copy-Item -Destination $LibDir

# Crear launcher Windows (.bat)
$batLines = @(
    "@echo off",
    "REM TicketSync Launcher - requiere Java 21 instalado con JAVA_HOME configurado",
    "set DIR=%~dp0",
    """%JAVA_HOME%\bin\java"" --module-path ""%DIR%lib"" --add-modules ALL-MODULE-PATH -m com.ticketsync/com.ticketsync.App %*"
)
$batLines -join "`r`n" | Out-File (Join-Path $OutDir "ticketsync.bat") -Encoding ASCII

# Crear launcher Linux/macOS (.sh)
$shContent = "#!/usr/bin/env bash`nset -euo pipefail`n" +
    "DIR=`$(cd `"`$(dirname `"`${BASH_SOURCE[0]}`")`" && pwd)`n" +
    "exec java --module-path `"`$DIR/lib`" --add-modules ALL-MODULE-PATH -m com.ticketsync/com.ticketsync.App `"`$@`"`n"
[System.IO.File]::WriteAllText((Resolve-Path $OutDir).Path + "\ticketsync.sh", $shContent, [System.Text.Encoding]::ASCII)

$total = (Get-ChildItem $LibDir -Filter "*.jar").Count
Write-Host "`nDistribucion lista en: $((Resolve-Path $OutDir).Path) ($total JARs en lib/)" -ForegroundColor Green
Write-Host ""
Write-Host "Para ejecutar en Windows:"
Write-Host "  dist\ticketsync.bat"
Write-Host ""
Write-Host "NOTA: El sistema destino necesita Java 21+ instalado con JAVA_HOME configurado."

# 3. Imagen nativa autosuficiente (opcional, no requiere WiX ni Java en el destino)
if ($Installer) {
    Write-Host "`n[3/3] Generando imagen nativa con jpackage (app-image)..." -ForegroundColor Yellow
    $InsDir = "dist\app-image"
    if (Test-Path $InsDir) { Remove-Item $InsDir -Recurse -Force }
    New-Item -ItemType Directory -Path $InsDir -Force | Out-Null

    jpackage `
        --type app-image `
        --name "TicketSync" `
        --app-version "1.0" `
        --input $LibDir `
        --main-jar $MainJar `
        --main-class "com.ticketsync.App" `
        --java-options "--module-path `$APPDIR --add-modules ALL-MODULE-PATH" `
        --dest $InsDir `
        --description "Sistema de gestion de tickets"
    if ($LASTEXITCODE -ne 0) { throw "jpackage fallo" }

    # Comprimir en .zip para distribucion
    $ZipPath = "dist\TicketSync-1.0.zip"
    if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
    Compress-Archive -Path "$InsDir\TicketSync" -DestinationPath $ZipPath
    Write-Host "`nImagen nativa: $((Resolve-Path $InsDir).Path)\TicketSync\" -ForegroundColor Green
    Write-Host "ZIP distribuible: $((Resolve-Path $ZipPath).Path)" -ForegroundColor Green
    Write-Host ""
    Write-Host "NOTA: El destino NO necesita Java instalado. Descomprimir y ejecutar:"
    Write-Host "  TicketSync\TicketSync.exe"
}

Write-Host "`n=== Empaquetado completado ===" -ForegroundColor Cyan

} finally { Pop-Location }