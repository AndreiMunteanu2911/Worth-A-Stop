
$ErrorActionPreference = "Stop"

# Always run from the project directory
Set-Location $PSScriptRoot

# Prefer existing process credentials; otherwise load private values from .env.
if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        if ($_ -match '^\s*(MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|GOOGLE_MAPS_SERVER_API_KEY|GOOGLE_MAPS_BROWSER_API_KEY)=(.*)$') {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($matches[1], "Process"))) {
                [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
            }
        }
    }
}

foreach ($name in @("MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
        throw "Missing $name. Set it in the process environment or copy .env.example to .env with private values. Existing MySQL volumes require their original credentials."
    }
}

$javaOutput = @(& java --version)
$javaExitCode = $LASTEXITCODE
$javaVersion = $javaOutput[0]
if ($javaExitCode -ne 0 -or $javaVersion -notmatch '^(openjdk|java) 21([. ]|$)') {
    throw "Java 21 must be active for Maven. Current: $javaVersion"
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
