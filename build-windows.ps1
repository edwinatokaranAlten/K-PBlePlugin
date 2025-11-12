# Build script for NIOX Communication Plugin - Windows Platform
# Builds Windows DLL (native, no Java dependency)

# Stop on errors
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "NIOX Windows SDK Build Script" -ForegroundColor Cyan
Write-Host "Building Windows Native DLL" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# Check if gradlew.bat exists
if (-Not (Test-Path "gradlew.bat")) {
    Write-Host "Error: gradlew.bat not found. Please run gradle wrapper first." -ForegroundColor Red
    exit 1
}

# Build Windows DLL
Write-Host "[1/1] Building Windows DLL..." -ForegroundColor Blue
Write-Host ""

try {
    & .\gradlew.bat mingwX64MainBinaries

    if ($LASTEXITCODE -ne 0) {
        throw "Build failed with exit code $LASTEXITCODE"
    }

    Write-Host ""
    Write-Host "✓ Windows DLL built successfully" -ForegroundColor Green
    Write-Host "   Location: build\bin\mingwX64\releaseShared\nioxplugin.dll" -ForegroundColor Gray

} catch {
    Write-Host ""
    Write-Host "✗ Windows DLL build failed" -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Green
Write-Host "Windows SDK Build Complete!" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green
Write-Host ""

# Check if DLL exists
$dllPath = "build\bin\mingwX64\releaseShared\nioxplugin.dll"
if (Test-Path $dllPath) {
    $dllInfo = Get-Item $dllPath
    Write-Host "Output file:" -ForegroundColor White
    Write-Host "  🪟 Windows DLL: $dllPath" -ForegroundColor White
    Write-Host "     Size: $([math]::Round($dllInfo.Length / 1KB, 2)) KB" -ForegroundColor Gray
    Write-Host "     Modified: $($dllInfo.LastWriteTime)" -ForegroundColor Gray
} else {
    Write-Host "Warning: DLL file not found at expected location" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  - Copy nioxplugin.dll to your Windows project directory" -ForegroundColor Gray
Write-Host "  - Use P/Invoke (C#) or LoadLibrary (C++) to load the DLL" -ForegroundColor Gray
Write-Host "  - See INTEGRATION_GUIDE.md for detailed integration instructions" -ForegroundColor Gray
Write-Host ""

# Additional build information
Write-Host "Build Information:" -ForegroundColor Cyan
Write-Host "  Platform: Windows (MinGW x64)" -ForegroundColor Gray
Write-Host "  Type: Native DLL (no Java dependency)" -ForegroundColor Gray
Write-Host "  Architecture: x86_64" -ForegroundColor Gray
Write-Host ""

Write-Host "Requirements for using the DLL:" -ForegroundColor Cyan
Write-Host "  - Windows 10 or later" -ForegroundColor Gray
Write-Host "  - Bluetooth LE capable adapter" -ForegroundColor Gray
Write-Host "  - Visual C++ Redistributable (may be required)" -ForegroundColor Gray
Write-Host ""
