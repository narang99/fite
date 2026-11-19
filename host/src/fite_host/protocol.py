"""Wire messages between the phone and the host, as described in handoff.md.

- Every text frame is JSON with a `type` and a version `v`.
- Parsing goes through one discriminated union, so an unknown or malformed frame fails in one place.
"""

from enum import StrEnum
from typing import Annotated, Literal

from pydantic import BaseModel, Field, TypeAdapter

PROTOCOL_VERSION = 0


class ErrorCode(StrEnum):
    INVALID_ARGS = "INVALID_ARGS"
    EXEC_FAILED = "EXEC_FAILED"


class Hello(BaseModel):
    type: Literal["hello"]
    v: int
    token: str
    device: dict


class Ping(BaseModel):
    type: Literal["ping"]


class UtteranceHeader(BaseModel):
    """Announces the binary WAV frame that follows it."""

    type: Literal["utterance"]
    v: int
    id: str
    format: Literal["wav"]
    sample_rate: int


PhoneMessage = Annotated[Hello | Ping | UtteranceHeader, Field(discriminator="type")]
_phone_message_adapter = TypeAdapter(PhoneMessage)


def parse_phone_message(text: str) -> Hello | Ping | UtteranceHeader:
    return _phone_message_adapter.validate_json(text)


def hello_ok() -> str:
    return f'{{"type":"hello_ok","v":{PROTOCOL_VERSION}}}'


def pong() -> str:
    return '{"type":"pong"}'


def ok_result(call_id: str, data: dict) -> str:
    return _result(call_id, ok=True, data=data)


def error_result(call_id: str, code: ErrorCode, message: str) -> str:
    return _result(call_id, ok=False, error={"code": code, "message": message})


def _result(call_id: str, ok: bool, **fields) -> str:
    result = {
        "type": "result",
        "v": PROTOCOL_VERSION,
        "id": call_id,
        "ok": ok,
        **fields,
    }
    return TypeAdapter(dict).dump_json(result).decode()
