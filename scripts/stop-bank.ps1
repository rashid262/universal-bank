# Stops everything this project runs locally: ngrok tunnel, Spring Boot backend,
# Vite frontend, and the local PostgreSQL server. Other Java/Node apps are left alone.
# Usage:  stop-bank            (from the project root)
#         stop-bank -DryRun    (only lists what would be stopped)
param([switch]$DryRun)

$project = Split-Path -Parent $PSScriptRoot
$pgBin = Join-Path $env:LOCALAPPDATA 'PostgreSQL\17-runtime\pgsql\bin'
$pgData = 'C:\Program Files\PostgreSQL\17\data'

function Stop-Matching($label, $filter) {
    $procs = @(Get-CimInstance Win32_Process | Where-Object $filter)
    if ($procs.Count -eq 0) { Write-Host "  $label : not running"; return }
    foreach ($p in $procs) {
        if ($DryRun) { Write-Host "  $label : would stop PID $($p.ProcessId) ($($p.Name))"; continue }
        try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop } catch { }
        Write-Host "  $label : stopped PID $($p.ProcessId) ($($p.Name))"
    }
}

Write-Host "Stopping Universal Bank services$(if ($DryRun) { ' (dry run)' })..."

# 1. Tunnel first, so the public URL goes offline before the backend does
Stop-Matching 'ngrok tunnel ' { $_.Name -eq 'ngrok.exe' }

# 2. Backend: Maven wrapper and the Spring Boot JVM started from this project
Stop-Matching 'backend      ' { $_.Name -eq 'java.exe' -and $_.CommandLine -like "*$project*" }

# 3. Frontend: the Vite dev server started from this project
Stop-Matching 'frontend     ' { $_.Name -eq 'node.exe' -and $_.CommandLine -like '*vite*' -and $_.CommandLine -like "*$project*" }

# 4. Local PostgreSQL: clean shutdown so no data is lost (the Neon database is unaffected)
$pgRunning = @(Get-CimInstance Win32_Process -Filter "Name='postgres.exe'").Count -gt 0
if (-not $pgRunning) {
    Write-Host '  postgres      : not running'
} elseif ($DryRun) {
    Write-Host '  postgres      : would run pg_ctl stop -m fast'
} else {
    & (Join-Path $pgBin 'pg_ctl.exe') stop -D $pgData -m fast | Out-Null
    Write-Host '  postgres      : stopped cleanly'
}

Write-Host 'Done. The Vercel site stays up but cannot reach the API until you start the backend and ngrok again.'
