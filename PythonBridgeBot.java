// ------------------------------------------------------------------
// PythonBridgeBot
// ------------------------------------------------------------------
// A Robocode Tank Royale bot that delegates its decision‑making logic to an
// external Python script (bot_logic.py). The Java side is responsible for:
//   • Spawning the Python process using ProcessBuilder
//   • Forwarding key game events to the Python program as single‑line JSON
//   • Parsing simple JSON commands coming back from Python and invoking the
//     corresponding Bot API calls (move, turn, fire, etc.)
//   • Processing everything sequentially each turn without background threads
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
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A minimal bridge bot that delegates strategy to a Python script.
 */
public class PythonBridgeBot extends Bot {

    // ── python process & I/O ──────────────────────────────────────────
    private Process pyProcess;
    private BufferedReader pyIn;
    private BufferedReader pyErr;
    private PrintWriter pyOut;
    private final Queue<String> eventQueue = new ArrayDeque<>();

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

        // Send initial connected event
        sendToPy("{\"event\":\"connected\",\"round\":" + getRoundNumber() + "}");

        // Main loop: execute one turn at a time
        // Events received during the turn are queued by the event handlers and
        // sent to Python after each go() call. The Python program replies with
        // simple commands that are executed before the next turn.
        while (isRunning()) {
            go();
            flushEvents();
        }
    }

    // ── event forwarding ─────────────────────────────────────────────
    @Override
    public void onScannedBot(ScannedBotEvent e) {
        eventQueue.offer(EventJson.scannedBot(e));
    }


    @Override
    public void onBulletHitWall(BulletHitWallEvent e) {
        eventQueue.offer(EventJson.bulletHitWall(e));
    }

    @Override
    public void onTick(TickEvent e) {
        eventQueue.offer(EventJson.tick(this, e));
    }

    @Override
    public void onWonRound(WonRoundEvent e) {
        eventQueue.offer(EventJson.wonRound(e));
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent e) {
        eventQueue.offer(EventJson.skippedTurn(e));
    }

    @Override
    public void onCustomEvent(CustomEvent e) {
        eventQueue.offer(EventJson.customEvent());
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        eventQueue.offer(EventJson.hitByBullet(e));
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        eventQueue.offer("{\"event\":\"hitWall\"}");
    }

    @Override
    public void onBotDeath(BotDeathEvent e) {
        eventQueue.offer(EventJson.botDeath(e));
    }

    @Override
    public void onRoundEnded(RoundEndedEvent e) {
        eventQueue.offer("{\"event\":\"roundEnded\"}");
    }

    @Override
    public void onDeath(DeathEvent e) {
        eventQueue.offer("{\"event\":\"death\"}");
        if (pyProcess != null) {
            pyProcess.destroy();
        }
    }

    // ── python process helpers ───────────────────────────────────────
    private void startPython() throws IOException {
        // Use -u for unbuffered stdout so we receive events immediately
        File script = new File("bot_logic.py");
        if (!script.isFile()) {
            // When executed from another directory, fall back to the jar location
            File jar = new File(PythonBridgeBot.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath());
            File jarDir = jar.getParentFile();
            script = new File(jarDir, "bot_logic.py");
        }
        if (!script.isFile()) {
            throw new IOException("Cannot locate bot_logic.py");
        }

        pyProcess = new ProcessBuilder("python", "-u", script.getAbsolutePath()).start();
        pyIn = new BufferedReader(new InputStreamReader(pyProcess.getInputStream()));
        pyErr = new BufferedReader(new InputStreamReader(pyProcess.getErrorStream()));
        pyOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(pyProcess.getOutputStream())), true);
    }

    private void sendToPy(String msg) {
        System.out.println("[ToPython] " + msg);
        pyOut.println(msg);
        pyOut.flush();
    }

    private void flushEvents() {
        while (!eventQueue.isEmpty()) {
            sendToPy(eventQueue.poll());
        }
        try {
            while (pyErr != null && pyErr.ready()) {
                System.err.println("[PyErr] " + pyErr.readLine());
            }
            while (pyIn != null && pyIn.ready()) {
                String line = pyIn.readLine();
                if (line == null) {
                    break;
                }
                System.out.println("[Python] " + line);
                handlePythonCommand(line.trim());
            }
        } catch (IOException ex) {
            logError("I/O with Python failed: " + ex.getMessage());
        }
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
                setFire(power);
                break;
            case "forward":
                setForward(distance);
                break;
            case "back":
                setBack(distance);
                break;
            case "turnLeft":
                setTurnLeft(angle);
                break;
            case "turnRight":
                setTurnRight(angle);
                break;
            case "turnGunLeft":
                setTurnGunLeft(angle);
                break;
            case "turnGunRight":
                setTurnGunRight(angle);
                break;
            case "turnRadarLeft":
                setTurnRadarLeft(angle);
                break;
            case "turnRadarRight":
                setTurnRadarRight(angle);
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
            return "{" +
                    "\"event\":\"scanned\"," +
                    "\"energy\":" + e.getEnergy() + "," +
                    "\"x\":" + e.getX() + "," +
                    "\"y\":" + e.getY() + "," +
                    "\"direction\":" + e.getDirection() + "," +
                    "\"speed\":" + e.getSpeed() +
                    "}";
        }

        static String hitByBullet(HitByBulletEvent e) {
            return "{" +
                    "\"event\":\"hitByBullet\"," +
                    "\"damage\":" + e.getDamage() + "," +
                    "\"direction\":" + e.getBullet().getDirection() +
                    "}";
        }

        static String bulletHitBot(BulletHitBotEvent e) {
            return "{" +
                    "\"event\":\"bulletHitBot\"," +
                    "\"damage\":" + e.getDamage() + "," +
                    "\"botId\":" + e.getVictimId() +
                    "}";
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
            return "{\"event\":\"wonRound\",\"turn\":" + e.getTurnNumber() + "}";
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
