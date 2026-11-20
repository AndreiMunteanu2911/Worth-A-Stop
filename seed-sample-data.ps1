$ErrorActionPreference = "Stop"

Push-Location $PSScriptRoot
try {
    # Keep accented place names intact when sending SQL to the MySQL client.
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    Get-Content -LiteralPath "scripts/sample-data.sql" -Raw -Encoding UTF8 |
        docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_PASSWORD exec mysql --batch --skip-reconnect --default-character-set=utf8mb4 --host=127.0.0.1 --user=$MYSQL_USER $MYSQL_DATABASE'

    if ($LASTEXITCODE -ne 0) {
        throw "Sample data could not be added. Start the app once to apply its database migrations, then check the MySQL error above."
    }
} finally {
    Pop-Location
}
