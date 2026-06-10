param(
    [switch]$PublicTunnel,
    [switch]$Rebuild,
    [int]$Port = 8090
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logsDir = Join-Path $root "logs"
$backendLog = Join-Path $root "backend-8090.log"
$backendErrLog = Join-Path $root "backend-8090.err.log"
$cloudflaredLog = Join-Path $root "cloudflared-8090.out.log"
$cloudflaredErrLog = Join-Path $root "cloudflared-8090.err.log"
$cloudflaredExe = Join-Path $root "tools\cloudflared.exe"
$cloudflaredUrl = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Import-EnvFile($path) {
    if (-not (Test-Path $path)) {
        return
    }

    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $parts = $line.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Get-AppJar {
    Get-ChildItem (Join-Path $root "backend\target") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Wait-ForHttp($url, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    return $false
}

function Get-ListenProcess($port) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Ensure-Cloudflared {
    if (Test-Path $cloudflaredExe) {
        return
    }

    Write-Step "Downloading cloudflared"
    New-Item -ItemType Directory -Force (Split-Path -Parent $cloudflaredExe) | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $cloudflaredUrl -OutFile $cloudflaredExe
}

New-Item -ItemType Directory -Force $logsDir | Out-Null

$envFile = Join-Path $root ".env"
$envExample = Join-Path $root ".env.example"
if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
    Copy-Item $envExample $envFile
    Write-Host "Created .env from .env.example. Edit .env later if API keys or Feishu credentials are required."
}

Import-EnvFile $envFile
$env:APP_PORT = [string]$Port
$env:LOG_PATH = if ($env:LOG_PATH) { $env:LOG_PATH } else { $logsDir }

$jar = Get-AppJar
if ($Rebuild -or -not $jar) {
    Write-Step "Building project"
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\setup-and-run.ps1") -NoStart
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed with exit code $LASTEXITCODE"
    }
    $jar = Get-AppJar
}

if (-not $jar) {
    throw "Backend jar was not found after build."
}

$listen = Get-ListenProcess $Port
if ($listen) {
    Write-Step "Backend is already listening on port $Port"
    Write-Host "Process ID: $($listen.OwningProcess)"
} else {
    Write-Step "Starting backend"
    $javaArgs = @("-jar", $jar.FullName)
    $process = Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $backendLog -RedirectStandardError $backendErrLog
    Write-Host "Backend process ID: $($process.Id)"
}

$localUrl = "http://localhost:$Port"
Write-Step "Checking local site"
if (-not (Wait-ForHttp $localUrl 60)) {
    throw "The backend did not respond at $localUrl within 60 seconds. Check backend-8090.log and backend-8090.err.log."
}

Write-Host ""
Write-Host "Local site is ready:" -ForegroundColor Green
Write-Host $localUrl

if ($PublicTunnel) {
    Ensure-Cloudflared
    Write-Step "Starting Cloudflare Quick Tunnel"
    $tunnelArgs = @("tunnel", "--protocol", "http2", "--url", $localUrl)
    $tunnel = Start-Process -FilePath $cloudflaredExe -ArgumentList $tunnelArgs -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $cloudflaredLog -RedirectStandardError $cloudflaredErrLog
    Write-Host "Tunnel process ID: $($tunnel.Id)"
    Write-Host "Waiting for public URL..."

    $publicUrl = $null
    $deadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Seconds 2
        $combined = ""
        if (Test-Path $cloudflaredLog) { $combined += (Get-Content $cloudflaredLog -Raw -ErrorAction SilentlyContinue) }
        if (Test-Path $cloudflaredErrLog) { $combined += "`n" + (Get-Content $cloudflaredErrLog -Raw -ErrorAction SilentlyContinue) }
        $match = [regex]::Match($combined, "https://[-a-zA-Z0-9.]+\.trycloudflare\.com")
        if ($match) {
            $publicUrl = $match.Value
            break
        }
    } while ((Get-Date) -lt $deadline)

    if ($publicUrl) {
        Write-Host ""
        Write-Host "Public demo URL is ready:" -ForegroundColor Green
        Write-Host $publicUrl
    } else {
        Write-Host ""
        Write-Host "Tunnel started, but the public URL was not detected yet. Check cloudflared-8090.err.log." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Keep this computer running while demonstrating the project."
