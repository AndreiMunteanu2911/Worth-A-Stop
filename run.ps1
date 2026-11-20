
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

function Test-Java21Jdk([string]$homePath) {
    if ([string]::IsNullOrWhiteSpace($homePath)) { return $false }

    try {
        $java = Join-Path $homePath "bin\java.exe"
        $javac = Join-Path $homePath "bin\javac.exe"
        if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
            -not (Test-Path -LiteralPath $javac -PathType Leaf)) {
            return $false
        }
        $version = @(& $java --version 2>$null)
        return ($LASTEXITCODE -eq 0 -and $version[0] -match '^(openjdk|java) 21([.+ -]|$)')
    } catch {
        return $false
    }
}

$jdkCandidates = @(
    $env:JAVA_HOME
    foreach ($command in (Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue)) {
        Split-Path -Parent (Split-Path -Parent $command.Source)
    }
    if ($env:ProgramFiles) {
        foreach ($vendor in "Eclipse Adoptium", "Java", "Microsoft") {
            Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles $vendor) -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object FullName
        }
    }
)
$javaHome = $jdkCandidates | Where-Object { Test-Java21Jdk $_ } | Select-Object -First 1
if (-not $javaHome) {
    throw "Java 21 JDK not found. Install a Java 21 JDK or point JAVA_HOME to its installation directory."
}

Write-Host "Starting MySQL container..." -ForegroundColor Cyan

docker compose up -d --wait

if ($LASTEXITCODE -ne 0) {
    throw "Failed to start MySQL."
}

Write-Host "Starting Spring Boot with Java 21 from $javaHome..." -ForegroundColor Green

$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $javaHome
    .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot failed to start."
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
