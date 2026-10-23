$ErrorActionPreference = "Stop"

$backendBaseUrl = if ($env:BACKEND_BASE_URL) {
    $env:BACKEND_BASE_URL.TrimEnd("/")
} else {
    "http://localhost:8080"
}

try {
    $response = Invoke-WebRequest -Uri "$backendBaseUrl/actuator/health" -Method Get
    if ($response.StatusCode -ne 200) {
        throw "Backend health endpoint returned status $($response.StatusCode)."
    }
    Write-Host "Backend health check passed."
} catch {
    Write-Error "Backend health check failed at $backendBaseUrl/actuator/health`: $_"
    exit 1
}
