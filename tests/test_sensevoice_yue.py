"""Tests the self-hosted Cantonese API protocol without downloading any model."""
import http.client
import importlib.util
import json
import threading
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

HERE = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("sensevoice_yue", HERE / "server/sensevoice_yue.py")
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)


class BackendProtocolTest(unittest.TestCase):
    def setUp(self):
        self.server = app.ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        self.server.api_token = "CantoneseToken1234567890"
        self.server.engine = mock.Mock()
        self.server.engine.infer.return_value = "我唔知"
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.worker.join(timeout=3)
        self.server.server_close()

    def request(self, method, path, body=b"", token=True, content_type="audio/mp4"):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        headers = {"Content-Type": content_type}
        if token:
            headers["Authorization"] = "Bearer CantoneseToken1234567890"
        conn.request(method, path, body=body, headers=headers)
        response = conn.getresponse()
        code = response.status
        data = json.loads(response.read())
        conn.close()
        return code, data

    def test_health_is_unloaded(self):
        code, payload = self.request("GET", "/health")
        self.assertEqual(code, 200)
        self.assertEqual(payload["model"], "SenseVoiceSmall")

    def test_unauthorized_never_executes_asr(self):
        status, _ = self.request("POST", app.TRANSCRIBE_PATH, b"a" * 256, token=False)
        self.assertEqual(status, 401)
        self.server.engine.infer.assert_not_called()

    def test_maximum_audio_limit(self):
        status, _ = self.request("POST", app.TRANSCRIBE_PATH, b"a" * (app.MAX_BYTES + 1))
        self.assertEqual(status, 413)
        self.server.engine.infer.assert_not_called()

    def test_wrong_type_rejected(self):
        status, _ = self.request("POST", app.TRANSCRIBE_PATH, b"a" * 300,
                                 content_type="text/plain")
        self.assertEqual(status, 415)
        self.server.engine.infer.assert_not_called()

    def test_raw_audio_contract_with_mocked_decoder(self):
        with mock.patch.object(app, "convert_m4a") as converter:
            converter.side_effect = lambda data, folder: Path(folder) / "utterance.wav"
            code, response = self.request("POST", app.TRANSCRIBE_PATH, b"a" * 300)
            self.assertEqual(code, 200)
            self.assertEqual(response["language"], "yue")
            self.assertEqual(response["text"], "我唔知")
            self.assertEqual(response["model"], "SenseVoiceSmall")
            converter.assert_called_once()
            self.server.engine.infer.assert_called_once()

    def test_unknown_route(self):
        code, payload = self.request("POST", "/v1/unknown", b"a" * 300)
        self.assertEqual(code, 404)


if __name__ == "__main__":
    unittest.main()
