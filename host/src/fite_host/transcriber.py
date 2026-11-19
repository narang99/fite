"""Speech to text, behind a small interface so the server can be tested without a model."""

import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Protocol

from parakeet_mlx import from_pretrained

PARAKEET_MODEL = "mlx-community/parakeet-tdt-0.6b-v3"


class Transcriber(Protocol):
    def transcribe(self, wav: bytes) -> str: ...


class ParakeetTranscriber:
    """MLX keeps its compute stream per thread.

    - A model loaded on one thread cannot run on another, which fails with "There is no Stream".
    - So one worker thread loads the model and runs every transcription.
    """

    def __init__(self, model_id: str = PARAKEET_MODEL):
        self._worker = ThreadPoolExecutor(max_workers=1)
        self._model = self._worker.submit(from_pretrained, model_id).result()

    def transcribe(self, wav: bytes) -> str:
        return self._worker.submit(self._transcribe, wav).result()

    def _transcribe(self, wav: bytes) -> str:
        """parakeet-mlx only reads from a path, so the bytes take a short trip through a temp file."""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "utterance.wav"
            path.write_bytes(wav)
            return self._model.transcribe(path).text.strip()
