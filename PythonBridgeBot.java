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
//   Python → Java  : "forward 150"  or  {"cmd":"forward","distance":150}
// Feel free to extend the EventJson helper or add new commands.
// ------------------------------------------------------------------

import dev.robocode.tankroyale.botapi.*;
import dev.robocode.tankroyale.botapi.events.*;

import java.util.HashMap;
import java.util.Map;

import java.io.*;
import java.net.URI;
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

    /**
     * Creates a PythonBridgeBot that connects to a specific server URL using a
     * secret for authentication. This is primarily used when the launcher
     * supplies connection details provided by Robocode Tank Royale.
     */
    public PythonBridgeBot(String serverUrl, String serverSecret) {
        super(BotInfo.fromFile("PythonBridgeBot.json"), URI.create(serverUrl), serverSecret);
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


    }

    // ── event forwarding ─────────────────────────────────────────────
    @Override
    public void onScannedBot(ScannedBotEvent e) {
        sendToPy(EventJson.scannedBot(e));
    }


    @Override
    public void onBulletHitWall(BulletHitWallEvent e) {
        sendToPy(EventJson.bulletHitWall(e));
    }

    @Override
    public void onTick(TickEvent e) {
        sendToPy(EventJson.tick(this, e));
    }

    @Override
    public void onWonRound(WonRoundEvent e) {
        sendToPy(EventJson.wonRound(e));
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent e) {
        sendToPy(EventJson.skippedTurn(e));
    }

    @Override
    public void onCustomEvent(CustomEvent e) {
        sendToPy(EventJson.customEvent());
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
        sendToPy("{\"event\":\"roundEnded\"}");
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
                System.out.println("[Python] " + line); // echo Python output to the console
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

    private void logError(String message) {
        System.err.println(message);
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
            Map<String, String> map = parseJson(cmd);
            if (map.containsKey("cmd")) {
                dispatchCommand(map.get("cmd"),
                        parseDouble(map.get("power")),
                        parseDouble(map.get("distance")),
                        parseDouble(map.get("angle")));
                return;
            }
        } catch (Exception ex) {
            logError("Cannot parse Python command: " + cmd + " (" + ex + ")");
        }

        String[] parts = cmd.split("\\s+");
        String c = parts[0];
        double val = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
        dispatchCommand(c, val, val, val);

    }

    private double parseDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return map;
        }
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return map;
        }
        String[] tokens = json.split(",");
        for (String token : tokens) {
            String[] pair = token.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = stripQuotes(pair[0].trim());
            String value = stripQuotes(pair[1].trim());
            map.put(key, value);
        }
        return map;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private void dispatchCommand(String cmd, double power, double distance, double angle) {
        switch (cmd) {
            case "fire":
                fire(power);
                break;
            case "forward":
                forward(distance);
                break;
            case "back":
                back(distance);
                break;
            case "turnLeft":
                turnLeft(angle);
                break;
            case "turnRight":
                turnRight(angle);
                break;
            case "turnGunLeft":
                turnGunLeft(angle);
                break;
            case "turnGunRight":
                turnGunRight(angle);
                break;
            case "turnRadarLeft":
                turnRadarLeft(angle);
                break;
            case "turnRadarRight":
                turnRadarRight(angle);
                break;
            case "rescan":
                rescan();
                break;
            default:
                logError("Unknown command from Python: " + cmd);
                break;
        }
    }

    // ── JSON builders for outgoing events ────────────────────────────
    private static class EventJson {
        static String scannedBot(ScannedBotEvent e) {
            return "{\"event\":\"scanned\",\"distance\":\"energy\":" + e.getEnergy() + "}";
        }

        static String hitByBullet(HitByBulletEvent e) {
            return "{\"event\":\"hitByBullet\",\"damage\":" + e.getDamage() + ",\"direction\":" + e.getBullet().getDirection() + "}";
        }

        static String bulletHitBot(BulletHitBotEvent e) {
            return "{\"event\":\"bulletHitBot\",\"damage\":" + e.getDamage() + ",\"botId\":" + e.getVictimId() + "}";
        }


        static String bulletHitWall(BulletHitWallEvent e) {
            return "{\"event\":\"bulletHitWall\"}";
        }


        static String tick(PythonBridgeBot bot, TickEvent e) {
            return "{" +
                    "\"event\":\"tick\"," +
                    "\"turn\":" + e.getTurnNumber() + "," +
                    "\"energy\":" + bot.getEnergy() + "," +
                    "\"x\":" + bot.getX() + "," +
                    "\"y\":" + bot.getY() + "," +
                    "\"direction\":" + bot.getDirection() + "," +
                    "\"gunDirection\":" + bot.getGunDirection() + "," +
                    "\"radarDirection\":" + bot.getRadarDirection() + "," +
                    "\"gunHeat\":" + bot.getGunHeat() + "," +
                    "\"speed\":" + bot.getSpeed() +
                    "}";
        }

        static String wonRound(WonRoundEvent e) {
            return "{\"event\":\"wonRound\",\"round\":}";
        }

        static String skippedTurn(SkippedTurnEvent e) {
            return "{\"event\":\"skippedTurn\",\"turn\":" + e.getTurnNumber() + "}";
        }

        static String customEvent() {
            return "{\"event\":\"custom\"}";
        }


        static String botDeath(BotDeathEvent e) {
            return "{\"event\":\"opponentDeath\",\"botId\":" + e.getVictimId() + "}";
        }
    }
}
