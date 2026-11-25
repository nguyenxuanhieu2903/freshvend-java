# ==============================================================================
# SCRIPT KHỞI ĐỘNG SPRING BOOT BACKEND CHO DỰ ÁN FRESHVEND
# File: scripts/start_backend.ps1
# ==============================================================================

$backendDir = Join-Path $PSScriptRoot "..\backend"
Set-Location $backendDir

$envFile = Join-Path $backendDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim()
            [System.Environment]::SetEnvironmentVariable($k, $v, "Process")
        }
    }
}

# Ensure critical variables are set
if (-not $env:DATABASE_PASSWORD) {
    $env:DATABASE_PASSWORD = "vending_dev_password"
}
if (-not $env:APP_ADMIN_JWT_SECRET) {
    $env:APP_ADMIN_JWT_SECRET = "super_secret_vending_machine_admin_jwt_key_2026_dev_min32chars"
}
if (-not $env:PORT) {
    $env:PORT = "8080"
}

Write-Host "Starting Spring Boot backend on port $env:PORT with database $env:DATABASE_URL..." -ForegroundColor Green
& ".\mvnw.cmd" spring-boot:run
