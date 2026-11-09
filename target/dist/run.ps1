# LowCarbon Campus System Launcher (PowerShell)
# Auto-detects JDK 21+ with proper version parsing

$ErrorActionPreference = "Continue"  # Don't interfere with stderr-based version checks
Set-Location $PSScriptRoot

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  LowCarbon Campus System" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

function Test-JavaMajor {
    param([string]$javaExe)
    if (-not (Test-Path $javaExe)) { return $false }
    try {
        # -version writes to stderr; capture without triggering PowerShell errors
        $v = (& $javaExe -version 2>&1 | Out-String -Width 200)
        # $v is a string like "openjdk version \"21.0.13.0.3\" 2026-10-24 LTS"
        if ($v -match 'version\s+"(\d+)(?:\.(\d+))?') {
            $major = [int]$Matches[1]
            $minor = if ($Matches[2]) { [int]$Matches[2] } else { 0 }
            # Handle legacy "1.8.0_212" -> real major is 8
            if ($major -eq 1 -and $minor -gt 0) { $major = $minor }
            if ($major -ge 21) {
                Write-Host "  [OK] JDK $major -> $javaExe" -ForegroundColor Green
                return $true
            }
            Write-Host "  [SKIP] Java $major (too old) -> $javaExe" -ForegroundColor DarkGray
            return $false
        }
        Write-Host "  [SKIP] Cannot parse version -> $javaExe" -ForegroundColor DarkGray
        return $false
    } catch {
        return $false
    }
}

$candidates = New-Object System.Collections.Generic.List[string]

# 1. Known good: BellSoft Liberica JDK 21
$candidates.Add("C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\java.exe")

# 2. Any BellSoft
$bs = Get-ChildItem "C:\Program Files\BellSoft" -Recurse -Filter "java.exe" -Depth 4 -ErrorAction SilentlyContinue
if ($bs) { $bs | ForEach-Object { $candidates.Add($_.FullName) } }

# 3. JAVA_HOME (but we must verify version!)
if ($env:JAVA_HOME) { $candidates.Add("$env:JAVA_HOME\bin\java.exe") }

# 4. Broad: *jdk* dirs
$jdkDirs = Get-ChildItem "C:\Program Files" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk|JDK|java|Java' }
if ($jdkDirs) { $jdkDirs | ForEach-Object { if (Test-Path "$($_.FullName)\bin\java.exe") { $candidates.Add("$($_.FullName)\bin\java.exe") } } }

# 5. PATH java (verify version!)
$pathJava = (Get-Command java -ErrorAction SilentlyContinue).Source
if ($pathJava) { $candidates.Add($pathJava) }

# Dedupe (keep order, remove empty/null)
$seen = @{}
$unique = foreach ($c in $candidates) {
    if ($c -and -not $seen.ContainsKey($c) -and (Test-Path $c)) {
        $seen[$c] = $true; $c
    }
}

Write-Host "Searching for JDK 21+..." -ForegroundColor Yellow
$java21 = $null
foreach ($c in $unique) {
    if (Test-JavaMajor $c) { $java21 = $c; break }
}

if (-not $java21) {
    Write-Host ""
    Write-Host "[ERROR] No JDK 21+ found on this system." -ForegroundColor Red
    Write-Host "Please install from https://adoptium.net/ or set JAVA_HOME to a JDK 21+." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
$jar = Join-Path $PSScriptRoot "lowcarbon-system.jar"
if (-not (Test-Path $jar)) {
    Write-Host "[ERROR] lowcarbon-system.jar not found in $PSScriptRoot" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "[OK] Launching..." -ForegroundColor Green
Write-Host ""
& $java21 -jar $jar