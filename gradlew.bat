@echo off
setlocal
set GRADLE_VERSION=8.13
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set CACHE=%GRADLE_USER_HOME%\wrapper\dists\agcodespace-gradle-%GRADLE_VERSION%
set DIST=%CACHE%\gradle-%GRADLE_VERSION%
if exist "%DIST%\bin\gradle.bat" goto run
if not exist "%CACHE%" mkdir "%CACHE%"
if not exist "%CACHE%\gradle.zip" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip -OutFile '%CACHE%\gradle.zip'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%CACHE%\gradle.zip' '%CACHE%'"
:run
call "%DIST%\bin\gradle.bat" %*
