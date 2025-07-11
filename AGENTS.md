# Project Agents.md Guide for OpenAI Codex

This Agents.md provides guidance for any AI agents contributing to this Robocode
relay project. The repository hosts a small Java\-based bot that forwards game
events to a Python script.

## Project Structure for OpenAI Codex Navigation

- `/`: Java sources and helper scripts
  - `PythonBridgeBot.java` and `MyFirstBot.java` are the two example bots.
  - `Launcher.java` is a small UI launcher used when starting the bot manually.
  - `bot_logic.py` contains the Python strategy logic executed by
    `PythonBridgeBot`.
  - `*.sh` and `*.bat` scripts compile and launch the bots.
- `/lib`: expected location of the Robocode Tank Royale API jars (not tracked).
- `/build`: directory where compiled class files are placed (ignored in git).

## Coding Conventions for OpenAI Codex

- Use **Java 11** for all Java code and keep indentation at four spaces.
- Use **Python 3** for the Python side of the relay.
- Follow the existing naming and formatting style in each file.
- Keep classes and methods small and focused. Add comments where logic is
  non\-trivial, especially around the JSON protocol between Java and Python.
- Do not modify files under `/build` or `/lib`.

## Running and Testing

This project does not have automated tests. To verify changes compile and run the
bots using the provided scripts:

```bash
# Compile all Java sources and start the launcher
./run.sh

# Compile and run the simple blocking relay bot
./run_myfirstbot.sh
```

Ensure the Java sources compile without errors and the Python script executes
correctly. If the scripts fail due to missing dependencies (e.g. the Robocode
jars), document the issue in the PR notes.

## Pull Request Guidelines for OpenAI Codex

When submitting a PR, please ensure it:

1. Clearly describes the changes made and the reason for them.
2. References any related issues if applicable.
3. Demonstrates that the bots compile and run using the scripts above.
4. Keeps the PR focused on a single concern.

## Programmatic Checks for OpenAI Codex

Before creating a PR, run the compile scripts shown in the **Running and
Testing** section. They must finish without errors. No additional tooling is
required.
