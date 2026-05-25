$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$toolsDir = Join-Path $root "tools"
$mavenVersion = "3.9.9"
$mavenDir = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenZip = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"

New-Item -ItemType Directory -Force $toolsDir | Out-Null

$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
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

    Write-Host "Loaded environment variables from .env"
}

if (-not (Test-Path (Join-Path $mavenDir "bin\mvn.cmd"))) {
    if (-not (Test-Path $mavenZip)) {
        Write-Host "Downloading Maven $mavenVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri $mavenUrl -OutFile $mavenZip
    }

    Write-Host "Extracting Maven..."
    Expand-Archive -Path $mavenZip -DestinationPath $toolsDir -Force
}

$env:APP_PORT = if ($env:APP_PORT) { $env:APP_PORT } else { "8090" }
$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:h2:mem:mindmap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "sa" }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }
$env:DB_DRIVER = if ($env:DB_DRIVER) { $env:DB_DRIVER } else { "org.h2.Driver" }
$env:DB_DIALECT = if ($env:DB_DIALECT) { $env:DB_DIALECT } else { "org.hibernate.dialect.H2Dialect" }

Write-Host "Starting backend on http://localhost:$env:APP_PORT"
& (Join-Path $mavenDir "bin\mvn.cmd") -f (Join-Path $root "backend\pom.xml") spring-boot:run
