Python Java Relay for Robocode Tank Royale

This project uses **Java 11** to remain compatible with the Robocode Tank Royale
API. Make sure JDK 11 is installed and available on your `PATH` before running
the helper scripts.

## Running without a JAR

Use `run.sh` (or `run.bat` on Windows) to compile the Java sources and start the launcher directly.
The script expects the Robocode Tank Royale API jars to be placed in a `lib` folder beside the sources.

```
./run.sh
```

For the simple blocking relay example that delegates all logic to Python, use
`run_myfirstbot.sh`:

```
./run_myfirstbot.sh
```
