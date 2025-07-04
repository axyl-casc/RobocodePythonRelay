import json
import sys

# Simple Python control logic for the bridge bot.
# Receives line-delimited JSON events from Java over stdin and sends
# simple commands back over stdout.

def handle_event(evt):
    if evt.get('event') == 'connected':
        # Move forward a bit when connected
        send_cmd('forward 150')
        send_cmd('turnRadarRight 360')  # initial scan
    elif evt.get('event') == 'tick':
        # Access bot state sent from Java
        energy = evt.get('energy')
        gun_heat = evt.get('gunHeat')
        # Example: fire when gun is cooled down
        if gun_heat == 0:
            send_cmd('fire 2.0')


def send_cmd(cmd):
    print(cmd, flush=True)

if __name__ == '__main__':
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
