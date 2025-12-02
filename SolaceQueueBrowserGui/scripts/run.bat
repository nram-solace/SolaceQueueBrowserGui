@echo off
setlocal enabledelayedexpansion
REM SolaceQueueBrowserGui Run Script (Windows)
REM Runs the application with a specified config file (defaults to config/default.json)
REM Note: Users should create config/default.json by copying config/sample-config.json
REM Supports master password for decrypting encrypted passwords

echo ==================================================
echo Starting SolaceQueueBrowserGui
echo ==================================================

REM Change to the project directory (parent of scripts/)
cd /d "%~dp0\.."

REM Parse command line arguments
set CONFIG_FILE=
set MASTER_PASSWORD=
set UI_PROFILE=
set SHOW_HELP=0

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="-c" (
    set CONFIG_FILE=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--config" (
    set CONFIG_FILE=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="-mp" (
    set MASTER_PASSWORD=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--master-password" (
    set MASTER_PASSWORD=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="-up" (
    set UI_PROFILE=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--ui-profile" (
    set UI_PROFILE=%~2
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--help" (
    set SHOW_HELP=1
    goto show_help
)
if /i "%~1"=="-h" (
    set SHOW_HELP=1
    goto show_help
)
REM Legacy support: if first argument doesn't start with - or /, treat as config file
if "%CONFIG_FILE%"=="" (
    set "ARG1=%~1"
    set "FIRST_CHAR=!ARG1:~0,1!"
    if not "!FIRST_CHAR!"=="-" if not "!FIRST_CHAR!"=="/" (
        set CONFIG_FILE=%~1
        shift
        goto parse_args
    )
)
echo Error: Unknown option: %~1
echo Use --help for usage information
exit /b 1

:show_help
if "%SHOW_HELP%"=="0" goto args_done
echo.
echo Usage: %~nx0 [options]
echo.
echo Options:
echo   -c, --config FILE              Configuration file (default: config/default.json)
echo   -mp, --master-password PWD     Master password for decrypting encrypted passwords
echo   -up, --ui-profile PROFILE      UI profile to use (Clean, Modern, Dark). Overrides config file setting
echo   -h, --help                      Show this help message
echo.
echo Examples:
echo   # Run with default config:
echo   %~nx0
echo.
echo   # Run with specific config:
echo   %~nx0 -c config/local-dev.json
echo.
echo   # Run with master password (for encrypted passwords):
echo   %~nx0 -c config/default.json --master-password "myMasterKey"
echo.
echo   # Run with UI profile override:
echo   %~nx0 -c config/default.json --ui-profile Dark
echo.
echo Note: Create config/default.json by copying config/sample-config.json and
echo       updating it with your specific broker connection details.
echo.
echo Important Notes:
echo   - Always quote the master password if it contains special characters
echo   - Special characters like #, $, !, etc. must be quoted: --master-password "pass#123"
echo   - If decryption fails, verify you're using the same master password used for encryption
echo   - Use interactive GUI prompt if command-line password handling is problematic
echo.
exit /b 0

:args_done

REM Set default config file if not provided
if "%CONFIG_FILE%"=="" (
    set CONFIG_FILE=config/default.json
    echo Using default config: %CONFIG_FILE%
) else (
    echo Using provided config: %CONFIG_FILE%
)

REM Check if config file exists
if not exist "%CONFIG_FILE%" (
    echo Error: Config file '%CONFIG_FILE%' not found!
    echo.
    echo Available config files:
    dir /b config\*.json 2>nul
    if errorlevel 1 echo   No .json files found in config/
    echo.
    echo Usage: %~nx0 [options]
    echo   Use --help for detailed usage information
    exit /b 1
)

REM Find the JAR file in target/ directory
set JAR_FILE=

for /f "delims=" %%f in ('dir /b target\SolaceQueueBrowserGui-*-jar-with-dependencies.jar 2^>nul') do (
    set JAR_FILE=target\%%f
    goto jar_found
)

:jar_found
if "!JAR_FILE!"=="" (
    echo Error: JAR file not found in target/ directory!
    echo.
    echo Please run 'scripts\build.bat' first to build the project
    exit /b 1
)

echo Starting application...
echo    JAR: %JAR_FILE%
echo    Config: %CONFIG_FILE%
if not "%MASTER_PASSWORD%"=="" (
    echo    Master Password: [provided]
)
if not "%UI_PROFILE%"=="" (
    echo    UI Profile: %UI_PROFILE%
)
echo.

REM Build Java command arguments
set JAVA_CMD=java -jar "%JAR_FILE%" -c "%CONFIG_FILE%"

REM Add master password if provided
if not "%MASTER_PASSWORD%"=="" (
    set JAVA_CMD=!JAVA_CMD! --master-password "%MASTER_PASSWORD%"
)

REM Add UI profile if provided
if not "%UI_PROFILE%"=="" (
    set JAVA_CMD=!JAVA_CMD! --ui-profile "%UI_PROFILE%"
)

REM Run the application
!JAVA_CMD!

