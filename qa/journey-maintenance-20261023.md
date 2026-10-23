# Journey maintenance — 2026-10-23

Source base: `0415666af574db40d0d1fceb8a18f22c94d043ed` on `main`.

Changes:

- Phone cancellation marks the control immediately, with native termination and connection/provider cleanup on a separate thread. A connection or stream attached after cancellation is released. Import owns the provider stream even when the device is unsupported or another task is running.
- Failed/cancelled imports remove partial files and preserve the existing model. A later import can retry after the worker exits.
- JourneyState restores its last successfully saved in-memory snapshot on failed or throwing commits; successful restore updates that snapshot. This addresses subsequent operations accidentally retaining a rejected mutation, without claiming filesystem power-loss guarantees.
- Recording now holds the screen-awake flag. Completion, failure, cancellation and navigation clear the flag and pending job deadline. Completed long downloads no longer retain their timeout callback for thirty minutes.

Executed locally:

- `tests/test-phone-install.sh`: 25 checks against production JourneyPhoneEngine, real ONNX Runtime 1.22.0 Java API, host storage/ABI fixtures. Includes blocked provider read, blocked disconnect, duplicate cancellation, late/interrupted attachment, checksum/length rejection, callback failure, cleanup, retained previous model and retry. No acoustic inference or real Android networking was executed by this suite.
- `tests/test-journey.sh`: 56 checks and curriculum verification (48 units, 144 bilingual utterances). New checks cover failed writes for level, listening, repeated/new recordings, recall, messages, clear and restore; throwing storage; retry; rollback after successful restore. SharedPreferences is a host fixture, not Android storage durability validation.
- `tests/test-phones.sh`: 31 existing phone compatibility checks.
- All Journey Java sources compiled against Android API and ONNX Runtime 1.22.0 with the existing compile-only legacy contracts.
- The existing voice-recovery CI command set also passed locally: voice buffer, conversation boundary, microphone capture, speech deadlines/streaming, exact APK restoration and cloud job cancellation/commit tests.

Packaged subsequently as original-signed 5.0.16 using the full 5.0.15 release; see `release-5.0.16.json`. Physical installation/upgrade, real Android provider cancellation, microphone/TTS interaction and on-device acoustic inference/performance remain unexecuted.
