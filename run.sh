#!/bin/sh
# Compile all Java sources and run the launcher.
# Requires the Robocode Tank Royale jars under ./lib

set -e
mkdir -p build
javac -d build -cp "lib/*" PythonBridgeBot.java Launcher.java
java -cp "lib/*:build" Launcher "$@"
