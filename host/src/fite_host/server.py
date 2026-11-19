"""WebSocket server the phone dials into.

- The phone connects, says hello with the shared token, then sends utterances.
- An utterance is a JSON header followed by one binary WAV frame.
- Each transcript is logged and returned in the result.
"""

import asyncio
import logging

from pydantic import ValidationError
from websockets.asyncio.server import ServerConnection, serve

from fite_host import protocol
from fite_host.protocol import ErrorCode, Hello, Ping, UtteranceHeader
from fite_host.transcriber import Transcriber

logger = logging.getLogger(__name__)


async def run_server(
    transcriber: Transcriber, token: str, host: str, port: int
) -> None:
    async def handle(connection: ServerConnection) -> None:
        await handle_phone(connection, transcriber, token)

    async with serve(handle, host, port):
        logger.info("listening on ws://%s:%d", host, port)
        await asyncio.Future()


async def handle_phone(
    connection: ServerConnection, transcriber: Transcriber, token: str
) -> None:
    if not await _accept_hello(connection, token):
        return
    logger.info("phone connected")
    pending: UtteranceHeader | None = None
    async for frame in connection:
        if isinstance(frame, str):
            pending = await _handle_text(connection, frame)
        else:
            await _handle_audio(connection, frame, pending, transcriber)
            pending = None
    logger.info("phone disconnected")


async def _accept_hello(connection: ServerConnection, token: str) -> bool:
    try:
        hello = protocol.parse_phone_message(await connection.recv(decode=True))
    except ValidationError:
        await connection.close(1008, "expected hello")
        return False
    if not isinstance(hello, Hello) or hello.token != token:
        await connection.close(1008, "bad hello")
        return False
    await connection.send(protocol.hello_ok())
    return True


async def _handle_text(
    connection: ServerConnection, frame: str
) -> UtteranceHeader | None:
    """Returns the header to hold until its audio arrives, or None for anything else."""
    try:
        message = protocol.parse_phone_message(frame)
    except ValidationError as error:
        await connection.send(
            protocol.error_result("", ErrorCode.INVALID_ARGS, str(error))
        )
        return None
    if isinstance(message, Ping):
        await connection.send(protocol.pong())
        return None
    if isinstance(message, UtteranceHeader):
        return message
    return None


async def _handle_audio(
    connection: ServerConnection,
    wav: bytes,
    header: UtteranceHeader | None,
    transcriber: Transcriber,
) -> None:
    if header is None:
        await connection.send(
            protocol.error_result(
                "", ErrorCode.INVALID_ARGS, "audio without an utterance header"
            )
        )
        return
    try:
        text = await asyncio.to_thread(transcriber.transcribe, wav)
    except Exception as error:
        logger.exception("transcription failed for %s", header.id)
        await connection.send(
            protocol.error_result(header.id, ErrorCode.EXEC_FAILED, str(error))
        )
        return
    logger.info("heard: %s", text)
    await connection.send(protocol.ok_result(header.id, {"text": text}))
