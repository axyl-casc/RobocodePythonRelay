#!/bin/sh
# Compile the bot
javac -cp lib/* PythonBridgeBot.java
# Run the bot
java -cp lib/*:. PythonBridgeBot
