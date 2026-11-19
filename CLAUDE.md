# Prose guidelines

- Apply these to code, docs, READMEs, PRs and commits.
- One idea per sentence.
- No em dashes.
- Use bullets, and nested bullets to continue a point.
- Be concise, but prefer simplicity over conciseness.

# Code guidelines

- Small functions that each do one thing, even if used once.
- Names carry the meaning, so skip docstrings that restate a signature.
- Docstrings explain why or how for the non-obvious parts.
- Strong types everywhere.
  - Python: pydantic models over dataclasses.
  - Kotlin: sealed types and data classes over strings and maps.
- Python is formatted by ruff, with imports sorted.

# Quick context

- A personal agent that operates the user's Pixel 8a, plus a voice path from the phone to the laptop.
- `handoff.md` is the original brief and the protocol contract.
- The phone always dials out as a WebSocket client.
- Security is deliberately light for now, since the phone is the only client.
  - Do not add wonky workarounds because of that.
  - A security pass comes later.

# Key files

| Path | Purpose |
|---|---|
| `handoff.md` | Brief and protocol contract |
| `android/` | Phone app (Kotlin, Compose) |
| `host/` | Laptop server (Python) |
| `scripts/` | Dev helpers, each doing one thing |

# Workflow

- One thing per PR.
- PRs are stacked with `gh stack`, so each one builds on the previous.
- Build the phone app with `./gradlew assembleDebug` after `. scripts/env.sh`.
