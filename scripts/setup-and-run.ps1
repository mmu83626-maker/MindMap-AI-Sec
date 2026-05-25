param(
    [switch]$SkipBuild,
    [switch]$NoStart
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$toolsDir = Join-Path $root "tools"
$mavenVersion = "3.9.9"
$mavenDir = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenZip = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Assert-Command($name, $installHint) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "$name was not found. $installHint"
    }
}

function Assert-LastCommand($label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$label failed with exit code $LASTEXITCODE"
    }
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

function Ensure-Maven() {
    New-Item -ItemType Directory -Force $toolsDir | Out-Null

    if (-not (Test-Path (Join-Path $mavenDir "bin\mvn.cmd"))) {
        if (-not (Test-Path $mavenZip)) {
            Write-Step "Downloading Maven $mavenVersion"
            Invoke-WebRequest -UseBasicParsing -Uri $mavenUrl -OutFile $mavenZip
        }

        Write-Step "Extracting Maven"
        Expand-Archive -Path $mavenZip -DestinationPath $toolsDir -Force
    }

    return Join-Path $mavenDir "bin\mvn.cmd"
}

function Copy-FrontendDistToBackend() {
    $frontendDist = Join-Path $root "frontend\dist"
    $backendStatic = Join-Path $root "backend\src\main\resources\static"

    if (-not (Test-Path $frontendDist)) {
        throw "Frontend dist directory was not found: $frontendDist"
    }

    if (Test-Path $backendStatic) {
        Remove-Item -LiteralPath $backendStatic -Recurse -Force
    }

    New-Item -ItemType Directory -Force $backendStatic | Out-Null
    Copy-Item -Path (Join-Path $frontendDist "*") -Destination $backendStatic -Recurse -Force
}

Write-Step "Checking prerequisites"
Assert-Command "java" "Install JDK 17 or newer, then rerun this script."
Assert-Command "node" "Install Node.js 18 or newer, then rerun this script."
Assert-Command "npm" "Install Node.js with npm, then rerun this script."

$envFile = Join-Path $root ".env"
$envExample = Join-Path $root ".env.example"
if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
    Copy-Item $envExample $envFile
    Write-Host "Created .env from .env.example. Edit .env to configure API keys or Feishu credentials."
}

Import-EnvFile $envFile
$env:APP_PORT = if ($env:APP_PORT) { $env:APP_PORT } else { "8090" }
$env:LOG_PATH = if ($env:LOG_PATH) { $env:LOG_PATH } else { Join-Path $root "logs" }
$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:h2:mem:mindmap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "sa" }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }
$env:DB_DRIVER = if ($env:DB_DRIVER) { $env:DB_DRIVER } else { "org.h2.Driver" }
$env:DB_DIALECT = if ($env:DB_DIALECT) { $env:DB_DIALECT } else { "org.hibernate.dialect.H2Dialect" }

$mvn = Ensure-Maven

if (-not $SkipBuild) {
    Write-Step "Installing frontend dependencies"
    Push-Location (Join-Path $root "frontend")
    if (Test-Path "package-lock.json") {
        npm ci
        Assert-LastCommand "npm ci"
    } else {
        npm install
        Assert-LastCommand "npm install"
    }

    Write-Step "Building frontend"
    npm run build
    Assert-LastCommand "npm run build"
    Pop-Location

    Write-Step "Embedding frontend into Spring Boot"
    Copy-FrontendDistToBackend

    Write-Step "Building backend jar"
    & $mvn -f (Join-Path $root "backend\pom.xml") clean package -DskipTests
    Assert-LastCommand "maven package"
}

if ($NoStart) {
    Write-Host ""
    Write-Host "Build finished. Start later with: powershell -ExecutionPolicy Bypass -File .\scripts\setup-and-run.ps1 -SkipBuild"
    exit 0
}

$jar = Get-ChildItem (Join-Path $root "backend\target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend jar was not found. Run without -SkipBuild first."
}

Write-Step "Starting NetScope AI"
Write-Host "Open http://localhost:$env:APP_PORT after the backend starts."
& java -jar $jar.FullName
Assert-LastCommand "java -jar"
