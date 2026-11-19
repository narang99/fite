#!/bin/sh
# Keeps the voice path up while you are away.
# - caffeinate stops the Mac from sleeping for as long as this runs.
# - Every few seconds it restores what tends to drop: the host process, adb reverse, and the phone's stay-awake setting.
# - It cannot unlock the phone. Leave it unlocked and charging.
cd "$(dirname "$0")/.." || exit 1
. scripts/env.sh

host_log=/tmp/fite-host.log

host_is_up() { pgrep -f "fite_host" >/dev/null; }
reverse_is_set() { adb reverse --list 2>/dev/null | grep -q "tcp:8765"; }

keep_alive() {
    host_is_up || (cd host && nohup uv run python -m fite_host >>"$host_log" 2>&1 &)
    reverse_is_set || adb reverse tcp:8765 tcp:8765 >/dev/null 2>&1
    adb shell svc power stayon true >/dev/null 2>&1
}

while true; do
    keep_alive
    sleep 5
done
