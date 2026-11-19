#!/bin/sh
# Lets the phone reach the laptop's host on 127.0.0.1:8765 over USB.
# adb forgets this on every replug and adb restart, so rerun it when the app cannot connect.
. "$(dirname "$0")/env.sh"
adb reverse tcp:8765 tcp:8765
