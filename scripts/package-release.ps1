$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$releaseRoot = Join-Path $root "dist\release"
$packageDir = Join-Path $releaseRoot "MindMap-AI-Sec"
$zipPath = Join-Path $releaseRoot "MindMap-AI-Sec-windows.zip"

Write-Host "Building application before packaging..."
& powershell -ExecutionPolicy Bypass -File (Join-Path $root "scripts\setup-and-run.ps1") -NoStart
if ($LASTEXITCODE -ne 0) {
    throw "Build failed with exit code $LASTEXITCODE"
}

$jar = Get-ChildItem (Join-Path $root "backend\target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend jar was not found after build."
}

if (Test-Path $packageDir) {
    Remove-Item -LiteralPath $packageDir -Recurse -Force
}
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

New-Item -ItemType Directory -Force $packageDir | Out-Null
Copy-Item $jar.FullName (Join-Path $packageDir "app.jar") -Force
Copy-Item (Join-Path $root ".env.example") (Join-Path $packageDir ".env.example") -Force
Copy-Item (Join-Path $root "scripts\start-release.ps1") (Join-Path $packageDir "start-release.ps1") -Force
Copy-Item (Join-Path $root "README.md") (Join-Path $packageDir "README.md") -Force
if (Test-Path (Join-Path $root "LICENSE")) {
    Copy-Item (Join-Path $root "LICENSE") (Join-Path $packageDir "LICENSE") -Force
}

Compress-Archive -Path (Join-Path $packageDir "*") -DestinationPath $zipPath -Force

Write-Host ""
Write-Host "Release package created:"
Write-Host $zipPath
