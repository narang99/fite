# Android app

The phone side of the agent, written in Kotlin with Jetpack Compose.

## Build

- `. scripts/env.sh` from the repo root sets `JAVA_HOME` and `ANDROID_HOME`.
- `cd android && ./gradlew installDebug` builds and installs on the connected phone.

## Use

- The launcher icon opens one screen with a big button.
- Hold the button while you speak, and release to send.
- The host's transcript replaces the status text.
- The first press only asks for microphone permission.
- Run `scripts/adb-reverse.sh` first over USB, so the phone can reach the host.

## Layout

- `PushToTalkScreen.kt`: the button and status text.
- `PushToTalk.kt`: press and release, as a small state machine.
- `Recorder.kt` and `Wav.kt`: microphone audio to a WAV.
- `HostClient.kt` and `Protocol.kt`: dial the host and send one utterance.
