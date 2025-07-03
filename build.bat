@echo off
setlocal

:: Step 1: Create build folder
if not exist build mkdir build

:: Step 2: Compile the Java files
javac -d build -cp "lib/*" PlayerBot.java Launcher.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

:: Step 3: Copy JSON config to build folder
copy /Y PlayerBot.json build\ > nul

:: Step 4: Create proper manifest file (2 lines!)
> build\manifest.txt (
    echo Main-Class: Launcher
    echo Class-Path: lib/robocode-tankroyale-bot-api-0.31.0.jar
)

:: Step 5: Package JAR
jar cfm PlayerBotLauncher.jar build\manifest.txt -C build .

:: Step 6: Clean up
rmdir /s /q build

endlocal
echo Build complete!
