#!/bin/sh
# Compile all Java sources and run the launcher.
# Requires the Robocode Tank Royale jars under ./lib

set -e
mkdir -p build
# Compile all available Java sources so both example bots can be run
javac --release 11 -d build -cp "lib/*" *.java
# Default to launching the original UI launcher
java -cp "lib/*:build" Launcher "$@"
