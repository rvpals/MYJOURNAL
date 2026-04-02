@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   JOURNAL Android App - Dependency Installer
echo ============================================================
echo.
echo   This script checks and installs all requirements needed
echo   to build the JOURNAL Android app.
echo.
echo   Requirements:
echo     1. JDK 17
echo     2. Android SDK (Platform 34, Build Tools)
echo     3. Gradle 8.5 (included via wrapper)
echo     4. AndroidX libraries (downloaded by Gradle)
echo.
echo ============================================================
echo.

:: ============================================================
:: STEP 1: Check for JDK 17
:: ============================================================
echo [Step 1/5] Checking for JDK 17...
echo.

set "JDK_FOUND="

:: Check JAVA_HOME first
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        for /f "tokens=*" %%i in ('"%JAVA_HOME%\bin\javac.exe" -version 2^>^&1') do set "JAVA_VER=%%i"
        echo   JAVA_HOME is set to: %JAVA_HOME%
        echo   Version: !JAVA_VER!
        echo !JAVA_VER! | findstr /C:"javac 17" >nul 2>&1
        if !errorlevel! equ 0 (
            set "JDK_FOUND=1"
            set "JDK_PATH=%JAVA_HOME%"
            echo   [OK] JDK 17 found via JAVA_HOME.
        ) else (
            echo   [WARN] JAVA_HOME points to a different JDK version.
        )
    )
)

:: Check common JDK 17 install locations
if not defined JDK_FOUND (
    for %%p in (
        "C:\Program Files\Java\jdk-17"
        "C:\Program Files\Eclipse Adoptium\jdk-17*"
        "C:\Program Files\Microsoft\jdk-17*"
        "C:\Program Files\Zulu\zulu-17*"
        "E:\Prog\Java\jdk-17"
    ) do (
        if not defined JDK_FOUND (
            for /d %%d in (%%p) do (
                if exist "%%d\bin\javac.exe" (
                    set "JDK_FOUND=1"
                    set "JDK_PATH=%%d"
                    echo   [OK] JDK 17 found at: %%d
                )
            )
        )
    )
)

if not defined JDK_FOUND (
    echo   [MISSING] JDK 17 not found.
    echo.
    echo   To install JDK 17, choose one of these options:
    echo.
    echo     Option A - Eclipse Temurin (recommended):
    echo       1. Go to: https://adoptium.net/temurin/releases/?version=17
    echo       2. Download the Windows x64 .msi installer
    echo       3. Run the installer (check "Set JAVA_HOME" during install)
    echo.
    echo     Option B - Oracle JDK:
    echo       1. Go to: https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
    echo       2. Download the Windows x64 installer
    echo       3. Run the installer
    echo       4. Set JAVA_HOME manually (see Step 4 below)
    echo.
    echo     Option C - winget (command line):
    echo       winget install EclipseAdoptium.Temurin.17.JDK
    echo.
    set "HAS_ERRORS=1"
) else (
    echo   Path: !JDK_PATH!
)
echo.

:: ============================================================
:: STEP 2: Check for Android SDK
:: ============================================================
echo [Step 2/5] Checking for Android SDK...
echo.

set "SDK_FOUND="

:: Check ANDROID_SDK_ROOT / ANDROID_HOME
for %%v in (ANDROID_SDK_ROOT ANDROID_HOME) do (
    if not defined SDK_FOUND (
        if defined %%v (
            call set "SDK_CHECK=%%%%v%%"
            if exist "!SDK_CHECK!\platform-tools" (
                set "SDK_FOUND=1"
                set "SDK_PATH=!SDK_CHECK!"
                echo   [OK] Android SDK found via %%v: !SDK_CHECK!
            )
        )
    )
)

:: Check common locations
if not defined SDK_FOUND (
    for %%p in (
        "%LOCALAPPDATA%\Android\Sdk"
        "%USERPROFILE%\AppData\Local\Android\Sdk"
        "C:\Android\android-sdk"
        "C:\Users\%USERNAME%\Android\Sdk"
    ) do (
        if not defined SDK_FOUND (
            if exist "%%~p\platform-tools" (
                set "SDK_FOUND=1"
                set "SDK_PATH=%%~p"
                echo   [OK] Android SDK found at: %%~p
            )
        )
    )
)

if not defined SDK_FOUND (
    echo   [MISSING] Android SDK not found.
    echo.
    echo   To install the Android SDK:
    echo.
    echo     Option A - Android Studio (includes SDK):
    echo       1. Download from: https://developer.android.com/studio
    echo       2. Run installer, SDK installs to: %LOCALAPPDATA%\Android\Sdk
    echo.
    echo     Option B - Command-line tools only (no IDE):
    echo       1. Go to: https://developer.android.com/studio#command-line-tools-only
    echo       2. Download "commandlinetools-win" zip
    echo       3. Create folder: C:\Android\android-sdk
    echo       4. Extract to: C:\Android\android-sdk\cmdline-tools\latest\
    echo       5. Run the SDK component install in Step 3 below
    echo.
    set "HAS_ERRORS=1"
) else (
    echo   Path: !SDK_PATH!
)
echo.

:: ============================================================
:: STEP 3: Check/Install SDK Components (Platform 34, Build Tools)
:: ============================================================
echo [Step 3/5] Checking Android SDK components...
echo.

if defined SDK_FOUND (
    :: Check for platform 34
    if exist "!SDK_PATH!\platforms\android-34" (
        echo   [OK] Android SDK Platform 34 installed.
    ) else (
        echo   [MISSING] Android SDK Platform 34.
        set "NEED_SDK_COMPONENTS=1"
    )

    :: Check for build tools
    set "BT_FOUND="
    for /d %%d in ("!SDK_PATH!\build-tools\34*") do (
        set "BT_FOUND=1"
    )
    if defined BT_FOUND (
        echo   [OK] Build tools for platform 34 installed.
    ) else (
        echo   [MISSING] Build tools for platform 34.
        set "NEED_SDK_COMPONENTS=1"
    )

    :: Check for platform-tools
    if exist "!SDK_PATH!\platform-tools\adb.exe" (
        echo   [OK] Platform tools (adb) installed.
    ) else (
        echo   [MISSING] Platform tools (adb).
        set "NEED_SDK_COMPONENTS=1"
    )

    if defined NEED_SDK_COMPONENTS (
        echo.
        echo   Installing missing SDK components...

        :: Find sdkmanager
        set "SDKMGR="
        if exist "!SDK_PATH!\cmdline-tools\latest\bin\sdkmanager.bat" (
            set "SDKMGR=!SDK_PATH!\cmdline-tools\latest\bin\sdkmanager.bat"
        )
        if not defined SDKMGR (
            for /d %%d in ("!SDK_PATH!\cmdline-tools\*") do (
                if exist "%%d\bin\sdkmanager.bat" set "SDKMGR=%%d\bin\sdkmanager.bat"
            )
        )

        if defined SDKMGR (
            echo   Using sdkmanager: !SDKMGR!
            echo.
            echo   Running: sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
            echo   (You may need to accept licenses)
            echo.
            call "!SDKMGR!" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
            if !errorlevel! equ 0 (
                echo.
                echo   [OK] SDK components installed successfully.
            ) else (
                echo.
                echo   [ERROR] SDK component installation failed.
                echo   Try running manually:
                echo     "!SDKMGR!" --licenses
                echo     "!SDKMGR!" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
                set "HAS_ERRORS=1"
            )
        ) else (
            echo.
            echo   [ERROR] sdkmanager not found. Install components manually:
            echo     sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
            echo   Or install them via Android Studio SDK Manager.
            set "HAS_ERRORS=1"
        )
    )
) else (
    echo   [SKIP] Cannot check SDK components without Android SDK.
)
echo.

:: ============================================================
:: STEP 4: Check/Accept SDK Licenses
:: ============================================================
echo [Step 4/5] Checking SDK licenses...
echo.

if defined SDK_FOUND (
    if exist "!SDK_PATH!\licenses\android-sdk-license" (
        echo   [OK] SDK licenses already accepted.
    ) else (
        set "SDKMGR="
        if exist "!SDK_PATH!\cmdline-tools\latest\bin\sdkmanager.bat" (
            set "SDKMGR=!SDK_PATH!\cmdline-tools\latest\bin\sdkmanager.bat"
        )
        if not defined SDKMGR (
            for /d %%d in ("!SDK_PATH!\cmdline-tools\*") do (
                if exist "%%d\bin\sdkmanager.bat" set "SDKMGR=%%d\bin\sdkmanager.bat"
            )
        )

        if defined SDKMGR (
            echo   Accepting SDK licenses...
            echo y | call "!SDKMGR!" --licenses >nul 2>&1
            echo   [OK] Licenses accepted.
        ) else (
            echo   [WARN] Cannot auto-accept licenses. Run manually:
            echo     sdkmanager --licenses
        )
    )
) else (
    echo   [SKIP] Cannot check licenses without Android SDK.
)
echo.

:: ============================================================
:: STEP 5: Verify Gradle Wrapper
:: ============================================================
echo [Step 5/5] Checking Gradle wrapper...
echo.

if exist "%~dp0gradlew.bat" (
    echo   [OK] gradlew.bat found.
) else (
    echo   [MISSING] gradlew.bat not found in android folder.
    set "HAS_ERRORS=1"
)

if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
    echo   [OK] gradle-wrapper.jar found.
) else (
    echo   [MISSING] gradle-wrapper.jar not found.
    set "HAS_ERRORS=1"
)

if exist "%~dp0gradle\wrapper\gradle-wrapper.properties" (
    echo   [OK] gradle-wrapper.properties found (Gradle 8.5).
) else (
    echo   [MISSING] gradle-wrapper.properties not found.
    set "HAS_ERRORS=1"
)

echo   Note: Gradle 8.5 and AndroidX libraries download automatically on first build.
echo.

:: ============================================================
:: SUMMARY
:: ============================================================
echo ============================================================
echo   SUMMARY
echo ============================================================
echo.

if defined JDK_FOUND (
    echo   JDK 17:           OK  (!JDK_PATH!)
) else (
    echo   JDK 17:           MISSING - See Step 1 above
)

if defined SDK_FOUND (
    echo   Android SDK:      OK  (!SDK_PATH!)
) else (
    echo   Android SDK:      MISSING - See Step 2 above
)

echo   Gradle 8.5:       Included via wrapper (auto-downloads)
echo   AndroidX libs:    Downloaded by Gradle on first build
echo.

if defined HAS_ERRORS (
    echo   [!] Some dependencies are missing. Follow the instructions above.
    echo.
) else (
    echo   All dependencies are installed!
    echo.
    echo   To build the APK, run:
    echo.
    if defined JDK_PATH (
        echo     set JAVA_HOME=!JDK_PATH!
    )
    if defined SDK_PATH (
        echo     set ANDROID_SDK_ROOT=!SDK_PATH!
    )
    echo     cd "%~dp0"
    echo     gradlew.bat assembleDebug
    echo.
    echo   Output: app\build\outputs\apk\debug\app-debug.apk
    echo.

    set /p "BUILD_NOW=   Build now? (y/n): "
    if /i "!BUILD_NOW!"=="y" (
        echo.
        echo   Building...
        echo.
        if defined JDK_PATH set "JAVA_HOME=!JDK_PATH!"
        if defined SDK_PATH set "ANDROID_SDK_ROOT=!SDK_PATH!"
        cd /d "%~dp0"
        call gradlew.bat assembleDebug
        if !errorlevel! equ 0 (
            echo.
            echo   Build successful!
            echo   APK: app\build\outputs\apk\debug\app-debug.apk
        ) else (
            echo.
            echo   Build failed. Check the errors above.
        )
    )
)

echo.
pause
endlocal
