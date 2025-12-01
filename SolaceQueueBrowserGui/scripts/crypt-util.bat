@echo off
REM SolaceQueueBrowserGui Password Encryption Script (Windows)
REM Wrapper script for encrypting/decrypting passwords using PasswordEncryptionCLI

REM Change to the project directory (parent of scripts/)
cd /d "%~dp0\.."

REM Find the JAR file in target/ directory
setlocal enabledelayedexpansion
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

REM Parse command line arguments
set MODE=encrypt
set INTERACTIVE=true
set PASSWORD=
set MASTER_KEY=

REM Check for help flag
if /i "%~1"=="--help" goto show_help
if /i "%~1"=="-h" goto show_help

REM Determine mode (encrypt or decrypt)
REM Default to encrypt if no mode specified
if /i "%~1"=="encrypt" (
    set MODE=encrypt
    shift
    goto check_args
)
if /i "%~1"=="decrypt" (
    set MODE=decrypt
    shift
    goto check_args
)

:check_args
REM Check if non-interactive mode (password and master key provided)
if not "%~2"=="" (
    set INTERACTIVE=false
    set PASSWORD=%~1
    set MASTER_KEY=%~2
    
    REM Run the CLI tool with provided arguments
    java -cp "%JAR_FILE%" com.solace.psg.util.PasswordEncryptionCLI %MODE% "%PASSWORD%" "%MASTER_KEY%"
) else (
    REM Interactive mode
    REM Run the CLI tool in interactive mode
    java -cp "%JAR_FILE%" com.solace.psg.util.PasswordEncryptionCLI %MODE%
)
goto end

:show_help
echo.
echo Usage: %~nx0 [command] [options]
echo.
echo Commands:
echo   encrypt [password] [master-key]  - Encrypt a password
echo   decrypt [encrypted] [master-key]  - Decrypt a password
echo   --help, -h                        - Show this help message
echo.
echo Examples:
echo   # Encrypt (interactive mode - recommended, passwords hidden):
echo   %~nx0 encrypt
echo.
echo   # Encrypt (non-interactive mode - for scripts):
echo   %~nx0 encrypt "myPassword" "masterKey"
echo.
echo   # Decrypt (interactive):
echo   %~nx0 decrypt
echo.
echo   # Decrypt (non-interactive):
echo   %~nx0 decrypt "ENC:AES256GCM:..." "masterKey"
echo.
echo Note: Always quote passwords with special characters!
goto end

:end

