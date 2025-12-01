# SolaceQueueBrowserGui Run Script (PowerShell)
# Runs the application with a specified config file (defaults to config/default.json)
# Note: Users should create config/default.json by copying config/sample-config.json
# Supports master password for decrypting encrypted passwords

param(
    [string]$c = "",
    [string]$config = "",
    [string]$mp = "",
    [string]$masterPassword = "",
    [switch]$h,
    [switch]$help
)

Write-Host "=================================================="
Write-Host "Starting SolaceQueueBrowserGui"
Write-Host "=================================================="

# Change to the project directory (parent of scripts/)
$scriptPath = Split-Path -Parent $PSCommandPath
Set-Location (Split-Path -Parent $scriptPath)

# Show help if requested
if ($h -or $help) {
    Write-Host ""
    Write-Host "Usage: .\scripts\run.ps1 [options]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -c, -config FILE           Configuration file (default: config/default.json)"
    Write-Host "  -mp, -masterPassword PWD   Master password for decrypting encrypted passwords"
    Write-Host "  -h, -help                  Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  # Run with default config:"
    Write-Host "  .\scripts\run.ps1"
    Write-Host ""
    Write-Host "  # Run with specific config:"
    Write-Host "  .\scripts\run.ps1 -c config\local-dev.json"
    Write-Host ""
    Write-Host "  # Run with master password (for encrypted passwords):"
    Write-Host "  .\scripts\run.ps1 -c config\default.json -mp 'myMasterKey'"
    Write-Host ""
    Write-Host "Note: Create config/default.json by copying config/sample-config.json and"
    Write-Host "      updating it with your specific broker connection details."
    Write-Host ""
    exit 0
}

# Determine config file
$configFile = ""
if ($config -ne "") {
    $configFile = $config
} elseif ($c -ne "") {
    $configFile = $c
} else {
    $configFile = "config\default.json"
    Write-Host "Using default config: $configFile"
}

if ($configFile -and $configFile -ne "config\default.json") {
    Write-Host "Using provided config: $configFile"
}

# Check if config file exists
if (-not (Test-Path $configFile)) {
    Write-Host "Error: Config file '$configFile' not found!"
    Write-Host ""
    Write-Host "Available config files:"
    $jsonFiles = Get-ChildItem -Path "config\*.json" -ErrorAction SilentlyContinue
    if ($jsonFiles) {
        $jsonFiles | ForEach-Object { Write-Host "  $($_.Name)" }
    } else {
        Write-Host "  No .json files found in config/"
    }
    Write-Host ""
    Write-Host "Usage: .\scripts\run.ps1 [options]"
    Write-Host "  Use -help for detailed usage information"
    exit 1
}

# Find the JAR file
$jarFile = $null

# First, check in target/ directory (development environment)
if (Test-Path "target") {
    $jarFile = Get-ChildItem -Path "target\SolaceQueueBrowserGui-*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
}

# If not found, check in current directory (distribution package)
if (-not $jarFile) {
    $jarFile = Get-ChildItem -Path "SolaceQueueBrowserGui-*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
}

# If still not found, check in parent directory
if (-not $jarFile) {
    $jarFile = Get-ChildItem -Path "..\SolaceQueueBrowserGui-*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
}

if (-not $jarFile) {
    Write-Host "Error: JAR file not found!"
    Write-Host ""
    Write-Host "Searched in:"
    Write-Host "  - target/ directory (development)"
    Write-Host "  - current directory (distribution)"
    Write-Host "  - parent directory"
    Write-Host ""
    Write-Host "If running from source, please run 'scripts\build.bat' first to build the project"
    Write-Host "If running from distribution, ensure the JAR file is in the same directory as this script"
    exit 1
}

Write-Host "Starting application..."
Write-Host "   JAR: $($jarFile.FullName)"
Write-Host "   Config: $configFile"

# Determine master password
$masterPass = ""
if ($masterPassword -ne "") {
    $masterPass = $masterPassword
} elseif ($mp -ne "") {
    $masterPass = $mp
}

if ($masterPass -ne "") {
    Write-Host "   Master Password: [provided]"
}
Write-Host ""

# Build and execute Java command
if ($masterPass -ne "") {
    & java -jar $jarFile.FullName -c $configFile --master-password $masterPass
} else {
    & java -jar $jarFile.FullName -c $configFile
}
