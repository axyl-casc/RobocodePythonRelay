#!/bin/sh
# Compile the bot
javac -cp lib/* PlayerBot.java
# Run the bot
java -cp lib/*:. PlayerBot
