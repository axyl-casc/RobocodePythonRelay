import json
import sys
import threading

"""Basic Python logic for the bridge bot.

This script mirrors the behaviour of the simple Java bot shown in the
repository README.  It starts a background thread that continuously
executes a movement pattern while the main thread listens for events
from the Java side and reacts to scans and bullet hits.
"""

# Current heading of our bot, updated on every tick
bot_direction = 0.0


def normalize_angle(angle: float) -> float:
    """Normalize angle to the range [-180, 180]."""
    while angle > 180:
        angle -= 360
    while angle < -180:
        angle += 360
    return angle


def main_loop():
    """Continuously perform the movement pattern from MyFirstBot."""
    while True:
        send_cmd("forward 100")
        send_cmd("turnGunLeft 360")
        send_cmd("back 100")
        send_cmd("turnGunLeft 360")


def handle_event(evt):
    global bot_direction
    event = evt.get("event")

    if event == "connected":
        # Start movement in the background when the bot connects
        threading.Thread(target=main_loop, daemon=True).start()
    elif event == "tick":
        # Track our current direction for bearing calculations
        bot_direction = evt.get("direction", bot_direction)
    elif event == "scanned":
        # Fire with low power whenever we see another bot
        send_cmd("fire 1")
    elif event == "hitByBullet":
        # Turn perpendicular to the incoming bullet
        bullet_dir = evt.get("direction", 0.0)
        bearing = normalize_angle(bullet_dir - bot_direction)
        turn_angle = 90 - bearing
        send_cmd(f"turnRight {turn_angle}")


def send_cmd(cmd: str):
    print(cmd, flush=True)


if __name__ == "__main__":
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
            handle_event(event)
        except json.JSONDecodeError:
            # Ignore malformed lines
            continue
