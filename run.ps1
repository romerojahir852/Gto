$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "PokerGTO.apk"

if (-not (Test-Path $apk)) {
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
}

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "       POKER GTO VISION - EJECUTOR E INSTALADOR" -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Archivo APK detectado: $apk" -ForegroundColor Green
Write-Host ""

if (-not (Test-Path $adb)) {
    Write-Host "[ERROR] No se encontró adb.exe en $adb" -ForegroundColor Red
    pause
    exit 1
}

Write-Host "Buscando dispositivos o emuladores Android conectados..." -ForegroundColor Gray
$devicesOutput = & $adb devices
Write-Host $devicesOutput

$lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match '\tdevice$' }

if ($lines.Count -gt 0) {
    Write-Host ""
    Write-Host "[OK] Dispositivo detectado ($($lines.Count) conectado(s))." -ForegroundColor Green
    Write-Host "Instalando APK en el dispositivo..." -ForegroundColor Yellow
    & $adb install -r "$apk"
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[OK] ¡Instalación exitosa!" -ForegroundColor Green
        Write-Host "Iniciando Poker GTO en la pantalla del dispositivo..." -ForegroundColor Cyan
        & $adb shell am start -n com.aistudio.pokergto.hvkp/com.example.MainActivity
        Write-Host ""
        Write-Host "¡La app está abierta y lista en tu dispositivo!" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Falló la instalación mediante ADB." -ForegroundColor Red
    }
} else {
    Write-Host ""
    Write-Host "[INFO] No se detectó ningún dispositivo o emulador activo." -ForegroundColor Yellow
    Write-Host "Opciones disponibles:" -ForegroundColor Gray
    Write-Host " 1. Conecta tu celular Android por cable USB con 'Depuración USB' activada y vuelve a ejecutar este archivo." -ForegroundColor White
    Write-Host " 2. Abre un emulador (BlueStacks, LDPlayer, WSA o Android Studio)." -ForegroundColor White
    Write-Host " 3. Puedes arrastrar el archivo PokerGTO.apk a tu emulador directamente." -ForegroundColor White
    Write-Host ""
    Write-Host "Abriendo la carpeta con el ejecutable PokerGTO.apk..." -ForegroundColor Cyan
    & explorer.exe /select,"$apk"
}

try {
    Write-Host ""
    Write-Host "Presiona cualquier tecla para continuar..." -ForegroundColor Gray
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} catch {}
