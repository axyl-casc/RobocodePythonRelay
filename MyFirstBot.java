import dev.robocode.tankroyale.botapi.*;
import dev.robocode.tankroyale.botapi.events.*;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Minimal bot that forwards game events to a Python script and waits for
 * commands in return. The Python program should output simple text commands
 * like "turnRight 90" which are executed on the Java side. Communication is
 * blocking, ensuring each turn waits for the Python logic.
 */
public class MyFirstBot extends Bot {

    private Process pyProcess;
    private BufferedReader pyIn;
    private PrintWriter pyOut;
    private final Queue<String> eventQueue = new ArrayDeque<>();

    public static void main(String[] args) {
        new MyFirstBot().start();
    }

    MyFirstBot() {
        super(BotInfo.fromFile("MyFirstBot.json"));
    }

    @Override
    public void run() {
        try {
            startPython();
        } catch (IOException e) {
            System.err.println("Unable to start Python: " + e.getMessage());
            return;
        }

        while (isRunning()) {
            go();
            flushEvents();
        }
    }

    // --- event handlers -------------------------------------------------
    @Override
    public void onTick(TickEvent e) {
        eventQueue.offer(EventJson.tick(this, e));
    }

    @Override
    public void onScannedBot(ScannedBotEvent e) {
        eventQueue.offer(EventJson.scannedBot());
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        eventQueue.offer(EventJson.hitByBullet(e));
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        eventQueue.offer(EventJson.hitWall());
    }

    // --- python communication helpers ----------------------------------
    private void startPython() throws IOException {
        File script = new File("bot_logic.py");
        if (!script.isFile()) {
            File jar = new File(MyFirstBot.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath());
            script = new File(jar.getParentFile(), "bot_logic.py");
        }
        if (!script.isFile()) {
            throw new IOException("Cannot locate bot_logic.py");
        }

        pyProcess = new ProcessBuilder("python", "-u", script.getAbsolutePath()).start();
        pyIn = new BufferedReader(new InputStreamReader(pyProcess.getInputStream()));
        pyOut = new PrintWriter(new OutputStreamWriter(pyProcess.getOutputStream()), true);
    }

    private void sendToPython(String msg) {
        pyOut.println(msg);
        pyOut.flush();
    }

    private void flushEvents() {
        while (!eventQueue.isEmpty()) {
            sendToPython(eventQueue.poll());
        }
        try {
            String line = pyIn.readLine(); // block for at least one command
            while (line != null && !line.isEmpty()) {
                handleCommand(line.trim());
                if (!pyIn.ready()) {
                    break;
                }
                line = pyIn.readLine();
            }
        } catch (IOException ex) {
            System.err.println("I/O with Python failed: " + ex.getMessage());
        }
    }

    // --- command parsing ------------------------------------------------
    private void handleCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        String c = parts[0];
        double val = parts.length > 1 ? parseDouble(parts[1]) : 0;
        switch (c) {
            case "fire":
                setFire(val);
                break;
            case "forward":
                setForward(val);
                break;
            case "back":
                setBack(val);
                break;
            case "turnLeft":
                setTurnLeft(val);
                break;
            case "turnRight":
                setTurnRight(val);
                break;
            case "turnGunLeft":
                setTurnGunLeft(val);
                break;
            case "turnGunRight":
                setTurnGunRight(val);
                break;
            case "turnRadarLeft":
                setTurnRadarLeft(val);
                break;
            case "turnRadarRight":
                setTurnRadarRight(val);
                break;
            case "rescan":
                rescan();
                break;
            default:
                System.err.println("Unknown command from Python: " + cmd);
                break;
        }
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // --- minimal JSON builders -----------------------------------------
    private static class EventJson {
        static String tick(MyFirstBot bot, TickEvent e) {
            return "{\"event\":\"tick\",\"turn\":" + e.getTurnNumber() +
                    ",\"direction\":" + bot.getDirection() +
                    ",\"speed\":" + bot.getSpeed() + "}";
        }

        static String scannedBot() {
            return "{\"event\":\"scanned\"}";
        }

        static String hitByBullet(HitByBulletEvent e) {
            return "{\"event\":\"hitByBullet\",\"direction\":" +
                    e.getBullet().getDirection() + "}";
        }

        static String hitWall() {
            return "{\"event\":\"hitWall\"}";
        }
    }
}
