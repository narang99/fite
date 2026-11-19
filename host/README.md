# Host

The laptop server the phone dials into.
It transcribes each utterance with Parakeet and logs the text.

## Run

- Needs `uv` and a working `ffmpeg`, since parakeet-mlx reads audio through it.
- `uv run python -m fite_host`
  - The first run downloads the model, about 2.5 GB.
  - It listens on `ws://127.0.0.1:8765`.
  - The shared token is `FITE_TOKEN`, defaulting to `dev-token`.
- Over USB, run `scripts/adb-reverse.sh` so the phone can reach the laptop's port.

## Test

- `uv run pytest`
- Tests use a fake transcriber, so they never load the model.

## Layout

- `protocol.py`: the wire messages from `handoff.md`.
- `transcriber.py`: speech to text behind a small interface.
- `server.py`: the WebSocket handler.
