@echo off
REM PocketDiary Dev Environment Setup
REM Run this in any new terminal to set up the Android dev environment

set JAVA_HOME=E:\dev\jdk-17
set ANDROID_HOME=E:\dev\android-sdk
set GRADLE_USER_HOME=E:\dev\.gradle
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%

echo ============================================
echo   PocketDiary Dev Environment Ready
echo ============================================
echo   JAVA_HOME        = %JAVA_HOME%
echo   ANDROID_HOME     = %ANDROID_HOME%
echo   GRADLE_USER_HOME = %GRADLE_USER_HOME%
echo ============================================
echo.
echo Available commands:
echo   gradlew assembleDebug    - Build debug APK
echo   gradlew installDebug     - Install to device/emulator
echo   gradlew test             - Run unit tests
echo   gradlew clean            - Clean build
echo   adb devices              - List connected devices
echo ============================================
