import asyncio
import json

from websockets.asyncio.client import connect
from websockets.asyncio.server import serve

from fite_host.server import handle_phone

TOKEN = "test-token"
HELLO = json.dumps({"type": "hello", "v": 0, "token": TOKEN, "device": {}})
UTTERANCE = json.dumps(
    {"type": "utterance", "v": 0, "id": "u-1", "format": "wav", "sample_rate": 16000}
)


class FakeTranscriber:
    def transcribe(self, wav: bytes) -> str:
        return f"heard {len(wav)} bytes"


class BrokenTranscriber:
    def transcribe(self, wav: bytes) -> str:
        raise RuntimeError("model fell over")


def converse(transcriber, frames: list[str | bytes], replies: int) -> list[str]:
    """Plays frames at a real server, and returns the first `replies` text frames it answers with."""

    async def run() -> list[str]:
        async def handle(connection):
            await handle_phone(connection, transcriber, TOKEN)

        async with serve(handle, "127.0.0.1", 0) as server:
            port = server.sockets[0].getsockname()[1]
            async with connect(f"ws://127.0.0.1:{port}") as phone:
                for frame in frames:
                    await phone.send(frame)
                return [await phone.recv() for _ in range(replies)]

    return asyncio.run(run())


def test_utterance_is_transcribed():
    replies = converse(FakeTranscriber(), [HELLO, UTTERANCE, b"12345"], replies=2)

    assert json.loads(replies[0])["type"] == "hello_ok"
    assert json.loads(replies[1]) == {
        "type": "result",
        "v": 0,
        "id": "u-1",
        "ok": True,
        "data": {"text": "heard 5 bytes"},
    }


def test_wrong_token_is_refused():
    wrong_hello = json.dumps({"type": "hello", "v": 0, "token": "nope", "device": {}})

    async def run():
        async def handle(connection):
            await handle_phone(connection, FakeTranscriber(), TOKEN)

        async with serve(handle, "127.0.0.1", 0) as server:
            port = server.sockets[0].getsockname()[1]
            async with connect(f"ws://127.0.0.1:{port}") as phone:
                await phone.send(wrong_hello)
                await phone.wait_closed()
                return phone.close_code

    assert asyncio.run(run()) == 1008


def test_audio_without_header_is_invalid_args():
    replies = converse(FakeTranscriber(), [HELLO, b"12345"], replies=2)

    assert json.loads(replies[1])["error"]["code"] == "INVALID_ARGS"


def test_failed_transcription_is_exec_failed():
    replies = converse(BrokenTranscriber(), [HELLO, UTTERANCE, b"12345"], replies=2)

    error = json.loads(replies[1])["error"]
    assert error == {"code": "EXEC_FAILED", "message": "model fell over"}
