import json
import random
import sys

# Current direction of our bot, updated each tick
bot_direction = 0.0
move_remaining = 0.0
TURN_OPTIONS = [-90, -45, 45, 90]


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
    global bot_direction, move_remaining
    event = evt.get("event")

    if event == "tick":
        bot_direction = evt.get("direction", bot_direction)
        speed = abs(evt.get("speed", 0.0))
        if move_remaining <= 0:
            distance = 150
            move_remaining = distance
            send(f"forward {distance}")
            angle = random.choice(TURN_OPTIONS)
            if angle > 0:
                send(f"turnRight {angle}")
            else:
                send(f"turnLeft {-angle}")
        else:
            move_remaining -= speed
        send("turnGunLeft 360")
    elif event == "scanned":
        send("fire 1")
    elif event == "hitByBullet":
        bullet_dir = evt.get("direction", 0.0)
        bearing = normalize(bullet_dir - bot_direction)
        send(f"turnRight {90 - bearing}")
    elif event == "hitWall":
        move_remaining = 0.0


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

