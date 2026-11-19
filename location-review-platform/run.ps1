
$ErrorActionPreference = "Stop"

# Always run from the project directory
Set-Location $PSScriptRoot

# Load environment variables from .env
if (-not (Test-Path ".env")) {
    throw "Missing .env file."
}

Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*(MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1],
            $matches[2],
            "Process"
        )
    }
}

Write-Host "Starting MySQL container..." -ForegroundColor Cyan

docker compose up -d --wait

if ($LASTEXITCODE -ne 0) {
    throw "Failed to start MySQL."
}

Write-Host "Starting Spring Boot..." -ForegroundColor Green

.\mvnw.cmd spring-boot:run

if ($LASTEXITCODE -ne 0) {
    throw "Spring Boot failed to start."
}
