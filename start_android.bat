@echo off
setlocal

set ANDROID_SDK_ROOT=C:\Android\android-sdk
set JAVA_HOME=E:\Prog\Java\jdk-17
set AVD_NAME=Pixel_API34
echo === Building APK ===
call "%~dp0gradlew.bat" assembleDebug
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

rem Find the latest built APK
set APK_PATH=
for %%f in ("%~dp0app\build\outputs\apk\debug\*.apk") do set APK_PATH=%%f
if "%APK_PATH%"=="" (
    echo No APK found!
    pause
    exit /b 1
)

echo === Starting emulator ===
start "" "%ANDROID_SDK_ROOT%\emulator\emulator" -avd %AVD_NAME% -no-snapshot-load -qemu -enable-kvm

echo === Waiting for device to boot ===
:wait_device
"%ANDROID_SDK_ROOT%\platform-tools\adb" wait-for-device
"%ANDROID_SDK_ROOT%\platform-tools\adb" shell getprop sys.boot_completed 2>nul | findstr "1" >nul
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    goto wait_device
)

echo === Enabling physical keyboard ===
"%ANDROID_SDK_ROOT%\platform-tools\adb" shell settings put secure show_ime_with_hard_keyboard 1
"%ANDROID_SDK_ROOT%\platform-tools\adb" shell setprop qemu.hw.mainkeys 0

echo === Installing APK ===
"%ANDROID_SDK_ROOT%\platform-tools\adb" install -r "%APK_PATH%"

echo === Launching app ===
"%ANDROID_SDK_ROOT%\platform-tools\adb" shell am start -n com.journal.app/.LoginActivity

echo === Done ===
pause
