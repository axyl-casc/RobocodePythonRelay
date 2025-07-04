import json
import sys


def debug(msg: str) -> None:
    """Print a debug message to stderr."""
    print(f"[DEBUG] {msg}", file=sys.stderr, flush=True)

"""Basic Python logic for the bridge bot.

This script mirrors the behaviour of the simple Java bot shown in the
repository README. The Java side forwards events as JSON and this
script reacts to them. Instead of running a separate background loop,
we invoke :func:`run` once whenever an event is processed (or when no
event is received).
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


def run():
    """Execute one iteration of the movement pattern from MyFirstBot."""
    debug("Executing run movement pattern")
    send_cmd("forward 100")
    send_cmd("turnGunLeft 360")
    send_cmd("back 100")
    send_cmd("turnGunLeft 360")


def handle_event(evt):
    global bot_direction
    event = evt.get("event")
    debug(f"Handling event: {evt}")

    if event == "connected":
        # Begin executing the movement pattern when the bot connects
        run()
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

    # Execute one iteration of the bot's default movement each turn
    if event != "run":
        run()


def send_cmd(cmd: str):
    debug(f"Sending command: {cmd}")
    print(cmd, flush=True)


if __name__ == "__main__":
    for line in sys.stdin:
        debug(f"Raw input: {line.rstrip()}")
        line = line.strip()
        if not line:
            handle_event({"event": "run"})
            continue
        try:
            event = json.loads(line)
            handle_event(event)
        except json.JSONDecodeError:
            # Use a synthetic run event when the input is not valid JSON
            debug("Invalid JSON received")
            handle_event({"event": "run"})
