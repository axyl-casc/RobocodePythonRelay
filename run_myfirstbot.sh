#!/bin/sh
# Compile and run the simple Python relay bot.
# Requires the Robocode Tank Royale API jars under ./lib
set -e
mkdir -p build
javac --release 11 -d build -cp "lib/*" MyFirstBot.java
java -cp "lib/*:build" MyFirstBot "$@"
