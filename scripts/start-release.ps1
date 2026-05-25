$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root ".env"
$jar = Join-Path $root "app.jar"

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

if (-not (Get-Command "java" -ErrorAction SilentlyContinue)) {
    throw "java was not found. Install JDK 17 or newer, then rerun this script."
}

if (-not (Test-Path $envFile) -and (Test-Path (Join-Path $root ".env.example"))) {
    Copy-Item (Join-Path $root ".env.example") $envFile
    Write-Host "Created .env from .env.example. Edit .env if API keys or Feishu credentials are required."
}

Import-EnvFile $envFile
$env:APP_PORT = if ($env:APP_PORT) { $env:APP_PORT } else { "8090" }
$env:LOG_PATH = if ($env:LOG_PATH) { $env:LOG_PATH } else { Join-Path $root "logs" }

if (-not (Test-Path $jar)) {
    throw "app.jar was not found in $root"
}

Write-Host "Starting NetScope AI on http://localhost:$env:APP_PORT"
& java -jar $jar
