@echo off
setlocal

set JAVA_HOME=E:\Prog\Java\jdk-17
set ANDROID_SDK_ROOT=C:\Android\android-sdk

echo === Building Release APK ===
call "%~dp0gradlew.bat" assembleRelease
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

rem Find the release APK
set APK_PATH=
for %%f in ("%~dp0app\build\outputs\apk\release\*.apk") do set APK_PATH=%%f

if "%APK_PATH%"=="" (
    echo No release APK found!
    pause
    exit /b 1
)

echo.
echo === Release APK built successfully ===
echo %APK_PATH%
echo.
pause
