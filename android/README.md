# Android app

The phone side of the agent, written in Kotlin with Jetpack Compose.

## Build

- `. scripts/env.sh` from the repo root sets `JAVA_HOME` and `ANDROID_HOME`.
- `cd android && ./gradlew installDebug` builds and installs on the connected phone.
