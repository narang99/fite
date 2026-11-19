# Handoff: Phone Agent (Android executor + laptop host)

You are Claude Code, running on the user's laptop. This document is your brief. Read it fully before doing anything.

## Goal

Build a personal agent system in which an AI agent on the laptop can operate the user's Pixel 8a (Android) in a safe and controlled way.

- **Phone app (Kotlin, Android): the primary endpoint.** It owns all device access, enforces policy, and executes a fixed set of typed tools. It uses **Shizuku** to get shell-level (uid 2000) privileges.
- **Laptop host (backend): written by the user by hand.** It accepts the phone's connection, relays tool calls from an agent, and exposes the phone's tools to agents (for example as an MCP server).
- **Transport for now: USB.** The design must work unchanged over a remote WebSocket later. Only the URL changes.

### Division of work

| Part | Who |
|---|---|
| Laptop dev environment setup (JDK, Android SDK, Gradle, Shizuku install) | **Claude Code** |
| Phone app (everything under `android/`) | **Claude Code** (the user is "vibe coding" this part) |
| Throwaway test harness to verify the phone app | **Claude Code** (keep it in `tools/harness/` and label it throwaway) |
| Laptop host / backend (`host/`) | **The user**. Do not write it unless asked. You may answer questions about it. |
| Protocol spec (this doc) | Shared contract. Change it only with the user's agreement. |

## Core design rules (do not violate)

1. **Brain is separate from hands.** The LLM is untrusted: it reads screens and notifications that can contain injected instructions. The phone app never accepts free-form shell commands. It exposes only typed tools with validated arguments, and each tool maps to a fixed command or API call.
2. **Policy lives on the phone.** Allowlists, denylists and confirmations are enforced in the phone app. The host and the agent can only *request* actions. Nothing upstream can turn policy off.
3. **The phone always dials out.** The phone app is a **WebSocket client**, and the laptop host is the **WebSocket server**. Over USB this works through `adb reverse`. Later it points at `wss://` on a server. This is what keeps the transport generic.
4. **Confirmations are out-of-band.** Actions that need approval are confirmed by the human on the phone (notification with Approve/Deny), never through the agent's channel.

## Architecture

```
[Agent: Claude Code / Agent SDK]
        |  MCP (stdio)
[Laptop host]  <-- WebSocket server on 127.0.0.1:8765
        ^
        |  USB: `adb reverse tcp:8765 tcp:8765`   (later: wss://your-server)
        |
[Phone app: WS client in a foreground service]
   ├── Policy engine (tiers, per-app rules, confirmations)
   ├── Tool registry (typed tools, arg validation)
   ├── Executor ──> Shizuku UserService (runs as shell uid 2000)
   ├── Audit log (append-only, on device)
   └── Persistent notification: "Agent active" + STOP button
```

## Protocol v0 (shared contract)

JSON text frames over WebSocket. Every message has `type` and `v` (protocol version, currently `0`).

**Phone → host, on connect:**
```json
{"type":"hello","v":0,"token":"<shared secret>","device":{"model":"Pixel 8a","sdk":35},
 "tools":[{"name":"media","description":"...","input_schema":{...},"tier":"auto"}]}
```
The host replies `{"type":"hello_ok","v":0}` or closes the connection. The tool list (JSON Schema for inputs) is the source of truth the host uses to expose MCP tools, so the host never hardcodes phone tools.

**Host → phone:**
```json
{"type":"call","v":0,"id":"c-123","tool":"open_uri","args":{"uri":"https://music.youtube.com/search?q=..."}}
```

**Phone → host:**
```json
{"type":"result","v":0,"id":"c-123","ok":true,"data":{...}}
{"type":"result","v":0,"id":"c-123","ok":false,"error":{"code":"DENIED_BY_POLICY","message":"..."}}
{"type":"pending","v":0,"id":"c-123","reason":"awaiting user confirmation"}
```
Error codes: `DENIED_BY_POLICY`, `USER_REJECTED`, `CONFIRM_TIMEOUT`, `INVALID_ARGS`, `SHIZUKU_UNAVAILABLE`, `EXEC_FAILED`, `UNKNOWN_TOOL`.

**Both directions:** `{"type":"ping"}` / `{"type":"pong"}` every 20 s. The phone reconnects with backoff (1 s, doubling, max 30 s).

**Untrusted data marking:** any tool output that contains on-screen or third-party text (`read_screen`, notifications) is returned under `data.untrusted_content`, so the host can wrap it for the agent as data, not instructions.

## Voice utterance (added after v0)

The first feature on top of the protocol: the user holds a button on the phone, speaks, and the host transcribes it.

**Phone → host:** a JSON header, followed by one binary frame holding the audio.
```json
{"type":"utterance","v":0,"id":"u-123","format":"wav","sample_rate":16000}
```
- The binary frame is a complete WAV file, mono, 16-bit PCM.
- Audio is binary so it is not inflated by base64 and the host can read it without decoding.

**Host → phone:** the usual `result`, with the transcript in `data.text`.
```json
{"type":"result","v":0,"id":"u-123","ok":true,"data":{"text":"play arijit singh"}}
```
- A failed transcription uses `ok: false` with `EXEC_FAILED`.
- A malformed header or an unsupported format uses `INVALID_ARGS`.

## Tools v0 (phone side)

| Tool | Args | Implementation (via Shizuku shell) | Default tier |
|---|---|---|---|
| `device_info` | none | getprop, `wm size`, battery | auto |
| `current_app` | none | `dumpsys window` (focused package/activity) | auto |
| `now_playing` | none | `dumpsys media_session` (parsed) | auto |
| `media` | `action`: play/pause/next/previous | `cmd media_session dispatch <action>` | auto |
| `open_uri` | `uri` | `am start -a android.intent.action.VIEW -d <uri>`; scheme/host allowlist | auto if allowlisted, else confirm |
| `launch_app` | `package` | `monkey -p <pkg> 1` or a launcher intent; package must be installed | per-app rule |
| `read_screen` | none | `uiautomator dump` → return a parsed, compact node list (text, content-desc, resource-id, bounds, clickable) | per-app rule |
| `screenshot` | none | `screencap -p` → base64 PNG | per-app rule |
| `tap` | `x`,`y` | `input tap` | per-app rule (foreground app) |
| `swipe` | `x1,y1,x2,y2,ms` | `input swipe` | per-app rule |
| `type_text` | `text` | `input text` (escape properly) | **confirm** by default |
| `key` | back/home/enter/recents | `input keyevent` | auto |

Every argument is validated against its schema, and the command is built as an argv array, never by string concatenation into `sh -c`.

## Policy v0

- **Per-app mode** applied to the *foreground* package: `deny` | `confirm` | `auto`.
  - Default `auto`: `com.google.android.apps.youtube.music`, launcher.
  - Default `deny` (hardcoded, not editable from the host): banking/UPI apps, password managers, authenticators, `com.android.settings`, the Shizuku manager (`moe.shizuku.privileged.api`), and this app itself.
  - Everything else: `confirm`.
- **Confirm flow:** post a high-priority notification showing the tool, args and foreground app, with Approve/Deny buttons. Send `pending` to the host, then wait up to 60 s, otherwise `CONFIRM_TIMEOUT`.
- **Kill switch:** the STOP button in the foreground notification closes the socket, stops the service and rejects all calls until it's re-enabled in the UI.
- **Audit log:** every call goes to an on-device log with timestamp, tool, args (truncated), foreground app, decision and result. It can be viewed in the app UI.
- Policy settings are edited only in the phone app UI.

## Phone app: implementation notes

- Kotlin, Jetpack Compose for a minimal UI: connection status, Shizuku status, server URL, token, per-app rules, audit log.
- **Foreground service** holds the WebSocket client (OkHttp is fine). Android 14+ requires a `foregroundServiceType`. Use `specialUse` (sideloaded, so no Play review) and check current constraints for the device's SDK.
- **POST_NOTIFICATIONS** runtime permission (Android 13+).
- **Cleartext to localhost:** `ws://127.0.0.1` needs a network security config that allows cleartext **only** for `127.0.0.1`/`localhost`. Everything else must be TLS.
- **Shizuku integration:** use the official Shizuku API library. Check the **current** Shizuku API docs/README before coding. The recommended way to run privileged code is a **UserService** (an AIDL service that Shizuku hosts in its shell-uid process), not the older `newProcess` call. The UserService exposes a narrow AIDL interface (for example `exec(argv: String[]): Result`, `readFile(path)`), and the app layer above it enforces policy. Handle these states: Shizuku not running, not installed, permission not granted, and binder death mid-call.
- The `read_screen` dump can be written to `/data/local/tmp/` by the UserService (shell can write there), read back and deleted inside the same UserService call.
- Store the token in EncryptedSharedPreferences or DataStore. Nothing sensitive goes in logs.

## Laptop setup (Claude Code does this)

1. Detect the OS and package manager. Report what's already installed before installing anything.
2. Install **JDK 17** (or whatever the current Android Gradle Plugin requires; check it).
3. Install the **Android SDK command-line tools**, then via `sdkmanager`: `platform-tools`, a `platforms;android-<N>` matching the phone, and matching `build-tools`. Set `ANDROID_HOME` / `ANDROID_SDK_ROOT` and `PATH`.
   - Find the phone's SDK level with `adb shell getprop ro.build.version.sdk`. Use it as `targetSdk`/`compileSdk` (keep `minSdk` at 30 or higher).
   - **Kotlin doesn't need a separate install.** It comes through the Kotlin Gradle plugin. Use the **Gradle wrapper** (`./gradlew`), and don't require a system Gradle.
   - Android Studio is optional; the project must build from the CLI.
4. `adb` is already working over USB; confirm with `adb devices`.
5. **Install Shizuku on the phone:** from the Play Store, or the official GitHub release APK via `adb install`. Start it **over USB** using the start command that the Shizuku app itself shows under "Start via ADB" (the path differs between versions, so copy it from the app rather than guessing). Verify that the Shizuku app shows "running".
6. Build and install the app with `./gradlew installDebug`. On first launch, grant notifications and approve the Shizuku permission dialog.
7. Run `adb reverse tcp:8765 tcp:8765`. This has to be redone after every replug, so the harness should do it automatically.

Ask the user before anything that needs sudo or changes their shell profile, and before installing anything on the phone besides Shizuku and this app.

## Milestones and acceptance tests

- **M0, environment:** `./gradlew assembleDebug` succeeds on an empty Compose app; `adb devices` shows the Pixel; Shizuku is running.
- **M1, connect:** the app connects through `adb reverse` to the throwaway harness (a minimal WS server in `tools/harness/`), completes `hello`/`hello_ok`, ping/pong stays alive for 10 min with the screen off, and it reconnects after unplugging and replugging.
- **M2, safe tools:** `device_info`, `current_app`, `now_playing`, `media` work from the harness. With YouTube Music playing, `media pause` pauses it.
- **M3, screen:** `read_screen` returns a compact node list for YouTube Music; `tap` on a node's center works; in a `deny` app (for example Settings) both return `DENIED_BY_POLICY`.
- **M4, confirmations:** `type_text` triggers a notification; Approve executes it, Deny returns `USER_REJECTED`, and doing nothing returns `CONFIRM_TIMEOUT`. STOP kills everything.
- **M5, end to end:** after the user writes the host, Claude Code (as the agent) asked to "play Arijit Singh on YouTube Music" completes the task using only these tools.

## Out of scope for now

- Remote WebSocket / server deployment (the design already allows for it).
- Root / Magisk.
- Running the LLM on the phone.
- Accessibility-service or notification-listener based features. These are restricted for sideloaded apps on recent Android anyway.

## Repo layout

```
android/        # phone app (Claude Code)
host/           # laptop backend (user)
tools/harness/  # throwaway WS server + scripts for testing the phone app (Claude Code)
HANDOFF.md      # this file
```

## Open decisions for the user

- Host language and framework.
- Whether the host's MCP server and WebSocket server run in one process or two.
- Initial per-app allowlist beyond YouTube Music.
