@echo off
setlocal

set "LOCAL_JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr"
set "LOCAL_MAVEN_CMD=%~dp0..\.tools\apache-maven-3.9.15\bin\mvn.cmd"

if "%JAVA_HOME%"=="" (
    if exist "%LOCAL_JAVA_HOME%\bin\java.exe" (
        set "JAVA_HOME=%LOCAL_JAVA_HOME%"
    )
)

if not "%JAVA_HOME%"=="" (
    if not exist "%JAVA_HOME%\bin\java.exe" (
        echo JAVA_HOME does not contain java.exe: "%JAVA_HOME%"
        exit /b 1
    )
)

if exist "%LOCAL_MAVEN_CMD%" (
    call "%LOCAL_MAVEN_CMD%" %*
    exit /b %ERRORLEVEL%
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found.
    echo Install Maven 3.8 or newer, or place it at "%LOCAL_MAVEN_CMD%".
    exit /b 1
)

call mvn %*
