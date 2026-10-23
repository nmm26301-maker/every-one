#!/usr/bin/env python3
"""Minimal, opt-in localhost Cantonese ASR for Everyone Android.

Behind a trusted HTTPS reverse proxy, never exposed directly to public Internet.
Only raw M4A bytes are accepted. Audio stays in a TemporaryDirectory.
SenseVoiceSmall is loaded lazily. Text transcription is not phoneme/tone scoring.
"""
import argparse
import hmac
import json
import os
import subprocess
import tempfile
import threading
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_BYTES = 2 * 1024 * 1024
MAX_SECONDS = 30
TRANSCRIBE_PATH = "/v1/yue/transcribe"
MODEL_NAME = "iic/SenseVoiceSmall"


def convert_m4a(data: bytes, folder: Path) -> Path:
    """Decode untrusted user audio with ffmpeg timeout and bound decoded duration."""
    inp, out = folder / "utterance.m4a", folder / "utterance.wav"
    inp.write_bytes(data)
    result = subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
         "-i", str(inp), "-t", "31", "-ar", "16000", "-ac", "1",
         "-c:a", "pcm_s16le", str(out)],
        check=False, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=35,
    )
    if result.returncode != 0:
        raise ValueError("无法解析 M4A 录音（需要 AAC/M4A 文件）")
    with wave.open(str(out), "rb") as wav:
        seconds = wav.getnframes() / wav.getframerate()
        if wav.getnchannels() != 1 or wav.getframerate() != 16000:
            raise ValueError("解码参数不符合要求")
    if seconds < 0.1 or seconds > MAX_SECONDS:
        raise ValueError("只接受 0.1–30 秒录音")
    return out


class SenseVoiceEngine:
    """Single lock serializes one model instance and prevents CPU/GPU overload."""

    def __init__(self):
        self._lock = threading.Lock()
        self._model = None

    def infer(self, audio: Path) -> str:
        with self._lock:
            if self._model is None:
                from funasr import AutoModel
                self._model = AutoModel(model=MODEL_NAME, disable_update=True)
            from funasr.utils.postprocess_utils import rich_transcription_postprocess
            results = self._model.generate(
                input=str(audio), cache={}, language="yue", use_itn=False
            )
            if not results or not isinstance(results[0], dict):
                return ""
            result = rich_transcription_postprocess(results[0].get("text", ""))
            return result.strip()[:2000]


class Handler(BaseHTTPRequestHandler):
    server_version = "EveryoneYueASR/1"

    def _json(self, status: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._json(200, {"ready": True, "model": "SenseVoiceSmall",
                             "note": "模型可能在首个请求时才加载"})
        else:
            self._json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != TRANSCRIBE_PATH:
            return self._json(404, {"error": "not_found"})
        expected = self.server.api_token
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied, "Bearer " + expected):
            return self._json(401, {"error": "unauthorized"})
        if self.headers.get("Content-Type", "").split(";")[0].strip() != "audio/mp4":
            return self._json(415, {"error": "audio/mp4 required"})
        try:
            length = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            length = -1
        if length < 256 or length > MAX_BYTES:
            return self._json(413, {"error": "audio too large or empty"})
        try:
            with tempfile.TemporaryDirectory(prefix="everyone-yue-") as temp:
                with Path(temp, "raw.bin").open("wb") as output:
                    remain = length
                    while remain:
                        block = self.rfile.read(min(65536, remain))
                        if not block:
                            raise ValueError("请求音频未接收完整")
                        output.write(block)
                        remain -= len(block)
                # Convert the exact validated bytes; temp directory is destroyed on exit.
                data = Path(temp, "raw.bin").read_bytes()
                wav = convert_m4a(data, Path(temp))
                transcript = self.server.engine.infer(wav)
            self._json(200, {"language": "yue", "model": "SenseVoiceSmall",
                             "text": transcript})
        except (ValueError, subprocess.TimeoutExpired) as exc:
            self._json(422, {"error": "invalid_audio", "message": str(exc)[:200]})
        except Exception:
            # Avoid leaking stack traces or audio data over the HTTP API.
            self.log_error("ASR failed; check local server logs")
            self._json(503, {"error": "model_unavailable"})

    def log_message(self, format, *args):
        # No request bodies, microphone content, or auth headers in server logs.
        return


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8765)
    args = p.parse_args()
    token = os.environ.get("EVERYONE_ASR_TOKEN", "")
    if len(token) < 16 or "\r" in token or "\n" in token:
        p.error("Set a random EVERYONE_ASR_TOKEN with at least 16 characters")
    if args.host not in ("127.0.0.1", "::1", "localhost"):
        p.error("Bind only localhost and expose through your own authenticated HTTPS proxy")
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.api_token = token
    server.engine = SenseVoiceEngine()
    print(f"Everyone Cantonese ASR at {args.host}:{args.port} (localhost only)")
    server.serve_forever()


if __name__ == "__main__":
    main()
