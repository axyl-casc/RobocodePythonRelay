@echo off
setlocal
if not exist build mkdir build
javac -d build -cp "lib/*" PythonBridgeBot.java Launcher.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)
java -cp "lib/*;build" Launcher %*
endlocal
