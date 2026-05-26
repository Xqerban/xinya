param(
    [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path (Split-Path -Parent $PSScriptRoot) "config.env"
}

function Read-EnvFile {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $index = $line.IndexOf("=")
        if ($index -le 0) {
            return
        }
        $key = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

$config = Read-EnvFile -Path $ConfigPath
$backend = "json"
if ($config.ContainsKey("STORAGE_BACKEND") -and $config["STORAGE_BACKEND"]) {
    $backend = $config["STORAGE_BACKEND"].ToLowerInvariant()
}

if (@("mysql", "database", "db") -notcontains $backend) {
    Write-Host "STORAGE_BACKEND=$backend, skip MySQL service check."
    exit 0
}

$preferredName = $env:MYSQL_SERVICE_NAME
if ([string]::IsNullOrWhiteSpace($preferredName) -and $config.ContainsKey("MYSQL_SERVICE_NAME")) {
    $preferredName = $config["MYSQL_SERVICE_NAME"]
}
if ([string]::IsNullOrWhiteSpace($preferredName)) {
    $preferredName = "MySQL"
}

$service = Get-Service -Name $preferredName -ErrorAction SilentlyContinue
if (-not $service) {
    $service = Get-Service | Where-Object {
        $_.Name -match "mysql" -or $_.DisplayName -match "mysql"
    } | Select-Object -First 1
}

if (-not $service) {
    Write-Warning "No MySQL Windows service found; startup script will still check database connection."
    exit 0
}

if ($service.Status -ne "Running") {
    Write-Host "Starting MySQL service: $($service.Name)"
    try {
        Start-Service -Name $service.Name -ErrorAction Stop
        $service.WaitForStatus("Running", [TimeSpan]::FromSeconds(30))
    } catch {
        Write-Error "Failed to start MySQL service: $($_.Exception.Message). Run the startup script as Administrator or start MySQL manually."
        exit 1
    }
}

$service = Get-Service -Name $service.Name
if ($service.Status -ne "Running") {
    Write-Error "MySQL service is not Running: $($service.Status)"
    exit 1
}

Write-Host "MySQL service is running: $($service.Name)"
