import argparse
import asyncio
import logging
import os

from fite_host.server import run_server
from fite_host.transcriber import ParakeetTranscriber


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Speech to text server for the phone app"
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    logging.info("loading the parakeet model, the first run downloads about 2.5 GB")
    transcriber = ParakeetTranscriber()
    token = os.environ.get("FITE_TOKEN", "dev-token")
    asyncio.run(run_server(transcriber, token, args.host, args.port))


if __name__ == "__main__":
    main()
