import json
import math
import sys

# Current direction of our bot, updated each tick
bot_direction = 0.0


def normalize(angle: float) -> float:
    """Normalize angle to [-180, 180]."""
    while angle > 180:
        angle -= 360
    while angle < -180:
        angle += 360
    return angle


def send(cmd: str) -> None:
    print(cmd, flush=True)


def handle_event(evt: dict) -> None:
    global bot_direction
    event = evt.get("event")

    if event == "tick":
        bot_direction = evt.get("direction", bot_direction)
        send("forward 100")
        send("turnGunLeft 360")
        send("back 100")
        send("turnGunLeft 360")
    elif event == "scanned":
        send("fire 1")
    elif event == "hitByBullet":
        bullet_dir = evt.get("direction", 0.0)
        bearing = normalize(bullet_dir - bot_direction)
        send(f"turnRight {90 - bearing}")


def main() -> None:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            evt = json.loads(line)
            handle_event(evt)
        except json.JSONDecodeError:
            continue


if __name__ == "__main__":
    main()

