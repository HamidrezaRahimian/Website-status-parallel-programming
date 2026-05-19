@echo off
setlocal

set "LOCAL_JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr"
set "JAR_PATH=%~dp0target\image-crawler-1.0.0.jar"

if "%JAVA_HOME%"=="" (
    if exist "%LOCAL_JAVA_HOME%\bin\java.exe" (
        set "JAVA_HOME=%LOCAL_JAVA_HOME%"
    )
)

if not "%JAVA_HOME%"=="" (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

if not "%JAVA_CMD%"=="java" if not exist "%JAVA_CMD%" (
    echo Java was not found at: "%JAVA_CMD%"
    exit /b 1
)

if not exist "%JAR_PATH%" (
    echo The application JAR does not exist yet.
    echo Run this first:
    echo   .\mvnw.cmd clean package
    exit /b 1
)

call "%JAVA_CMD%" -jar "%JAR_PATH%" %*
