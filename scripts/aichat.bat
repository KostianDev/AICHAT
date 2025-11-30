@echo off
REM AICHAT Launcher for Windows
REM Automatically configures OpenCL environment

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Detect Java
if defined JAVA_HOME (
    set JAVA=%JAVA_HOME%\bin\java.exe
) else (
    where java >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set JAVA=java
    ) else (
        echo Error: Java not found. Please install Java 25+ or set JAVA_HOME
        pause
        exit /b 1
    )
)

echo AICHAT - Advanced Image Color Harmony Analysis
echo ================================================

set NATIVE_DIR=%SCRIPT_DIR%native\windows
set NATIVE_LIB=%NATIVE_DIR%\aichat_native.dll

if exist "%NATIVE_LIB%" (
    echo Native acceleration: enabled
    set PATH=%NATIVE_DIR%;%PATH%
) else (
    echo Native library not found, using Java fallback
)

REM Build module path for JavaFX
set MODULEPATH=
for %%j in ("%SCRIPT_DIR%lib\javafx-*.jar") do (
    if defined MODULEPATH (
        set MODULEPATH=!MODULEPATH!;%%j
    ) else (
        set MODULEPATH=%%j
    )
)

REM Build classpath for other jars (skip JavaFX)
set CLASSPATH=
for %%j in ("%SCRIPT_DIR%lib\*.jar") do (
    echo %%j | findstr /i "javafx-" >nul
    if errorlevel 1 (
        if defined CLASSPATH (
            set CLASSPATH=!CLASSPATH!;%%j
        ) else (
            set CLASSPATH=%%j
        )
    )
)

REM OpenCL is usually auto-detected on Windows via GPU drivers
REM NVIDIA, AMD, and Intel drivers install OpenCL.dll in System32

REM Run application with JavaFX as modules
"%JAVA%" ^
    --module-path "%MODULEPATH%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.swing ^
    --enable-native-access=javafx.graphics,ALL-UNNAMED ^
    -Djava.library.path="%NATIVE_DIR%" ^
    -cp "%CLASSPATH%" ^
    aichat.App %*

if %ERRORLEVEL% neq 0 pause
