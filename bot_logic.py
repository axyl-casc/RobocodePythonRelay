import json
import sys

# Simple Python control logic for the bridge bot.
# Receives line-delimited JSON events from Java over stdin and sends
# simple commands back over stdout.

def handle_event(evt):
    if evt.get('event') == 'connected':
        # Move forward a bit when connected
        send_cmd('forward 150')
    elif evt.get('event') == 'tick':
        pass  # placeholder for more logic


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
