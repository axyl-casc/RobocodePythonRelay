// ------------------------------------------------------------------
// PythonBridgeBot
// ------------------------------------------------------------------
// A Robocode Tank Royale bot that delegates its decision‑making logic to an
// external Python script (bot_logic.py). The Java side is responsible for:
//   • Spawning the Python process using ProcessBuilder
//   • Forwarding key game events to the Python program as single‑line JSON
//   • Parsing simple JSON commands coming back from Python and invoking the
//     corresponding Bot API calls (move, turn, fire, etc.)
//
// Communication protocol (line‑delimited JSON):
//   Java → Python  : {"event":"scanned","distance":123.4,"energy":87.6}
//   Python → Java  : {"cmd":"fire","power":2.5}
// Feel free to extend the EventJson helper or add new commands.
// ------------------------------------------------------------------

import dev.robocode.tankroyale.botapi.*;
import dev.robocode.tankroyale.botapi.events.*;

import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A minimal bridge bot that delegates strategy to a Python script.
 */
public class PythonBridgeBot extends Bot {

    // ── python process & I/O ──────────────────────────────────────────
    private Process pyProcess;
    private BufferedReader pyIn;
    private PrintWriter pyOut;
    private final BlockingQueue<String> outgoing = new LinkedBlockingQueue<>();

    // ── entry point ──────────────────────────────────────────────────
    public static void main(String[] args) {
        new PythonBridgeBot().start();
    }

    // ── constructor ──────────────────────────────────────────────────
    public PythonBridgeBot() {
        super(BotInfo.fromFile("PythonBridgeBot.json"));
    }

    // ── main loop ────────────────────────────────────────────────────
    @Override
    public void run() {
        try {
            startPython();
        } catch (IOException e) {
            logError("Could not start Python process: " + e.getMessage());
            return; // abort bot if Python cannot be launched
        }

        // Pipe I/O in background threads so the bot thread stays responsive
        new Thread(this::readFromPython, "python-reader").start();
        new Thread(this::writeToPython, "python-writer").start();

        // Notify Python that the bot is ready
        sendToPy("{\"event\":\"connected\",\"round\":" + getRoundNumber() + "}");

        // Idle – all actions are dictated from Python commands
        while (isRunning()) {
            execute();
        }
    }

    // ── event forwarding ─────────────────────────────────────────────
    @Override
    public void onScannedBot(ScannedBotEvent e) {
        sendToPy(EventJson.scannedBot(e));
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        sendToPy(EventJson.hitByBullet(e));
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        sendToPy("{\"event\":\"hitWall\"}");
    }

    @Override
    public void onBotDeath(BotDeathEvent e) {
        sendToPy(EventJson.botDeath(e));
    }

    @Override
    public void onRoundEnded(RoundEndedEvent e) {
        sendToPy("{\"event\":\"roundEnded\",\"rank\":" + e.getRank() + "}");
    }

    @Override
    public void onDeath(DeathEvent e) {
        sendToPy("{\"event\":\"death\"}");
        if (pyProcess != null) {
            pyProcess.destroy();
        }
    }

    // ── python process helpers ───────────────────────────────────────
    private void startPython() throws IOException {
        // Use -u for unbuffered stdout so we receive events immediately
        pyProcess = new ProcessBuilder("python", "-u", "bot_logic.py").start();
        pyIn = new BufferedReader(new InputStreamReader(pyProcess.getInputStream()));
        pyOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(pyProcess.getOutputStream())), true);
    }

    private void readFromPython() {
        String line;
        try {
            while ((line = pyIn.readLine()) != null) {
                handlePythonCommand(line.trim());
            }
        } catch (IOException ex) {
            logError("Lost connection to Python: " + ex.getMessage());
        }
    }

    private void writeToPython() {
        try {
            while (isRunning()) {
                String msg = outgoing.take();
                pyOut.println(msg);
            }
        } catch (InterruptedException ignored) {
            // Bot shutting down
        }
    }

    private void sendToPy(String msg) {
        outgoing.offer(msg);
    }

    // ── command parser ───────────────────────────────────────────────
    private void handlePythonCommand(String cmd) {
        /*
         * Supported commands (extend as needed):
         *   {"cmd":"fire",       "power": 2.0}
         *   {"cmd":"forward",    "distance": 100}
         *   {"cmd":"back",       "distance": 80}
         *   {"cmd":"turn",       "angle": 90}
         *   {"cmd":"turnGun",    "angle": 45}
         *   {"cmd":"turnRadar",  "angle": 360}
         */
        try {
            var json = new org.json.JSONObject(cmd);
            switch (json.getString("cmd")) {
                case "fire" -> fire(json.getDouble("power"));
                case "forward" -> forward(json.getDouble("distance"));
                case "back" -> back(json.getDouble("distance"));
                case "turn" -> turnRight(json.getDouble("angle"));
                case "turnGun" -> turnGunRight(json.getDouble("angle"));
                case "turnRadar" -> turnRadarRight(json.getDouble("angle"));
                default -> logError("Unknown command from Python: " + cmd);
            }
        } catch (Exception ex) {
            logError("Cannot parse Python command: " + cmd + " (" + ex + ")");
        }
    }

    // ── JSON builders for outgoing events ────────────────────────────
    private static class EventJson {
        static String scannedBot(ScannedBotEvent e) {
            return "{\"event\":\"scanned\",\"distance\":" + e.getDistance() + ",\"energy\":" + e.getEnergy() + "}";
        }

        static String hitByBullet(HitByBulletEvent e) {
            return "{\"event\":\"hitByBullet\",\"damage\":" + e.getDamage() + ",\"direction\":" + e.getBullet().getDirection() + "}";
        }

        static String botDeath(BotDeathEvent e) {
            return "{\"event\":\"opponentDeath\",\"botId\":" + e.getVictimId() + "}";
        }
    }
}
