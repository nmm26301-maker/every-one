# Everyone English 3.0 validation

Validation date: 2026-10-08, Asia/Shanghai.

## Earlier checkpoint Android validation

- Java compilation / D8 and APK v1, v2, v3 signature verification passed. The signing certificate is unchanged from 2.0.
- 17 standalone algorithm checks passed: SRT/VTT parsing, timing, text comparison and review scheduling. Text comparison now uses rolling rows to bound memory.
- APK asset check passed: DEX, 60 lessons, 10 offline book chapters, real synthetic AAC audio with 10 timestamps, 20,000 dictionary entries and 23,559 inflected forms, dictionary provenance and licenses.
- Actual signed upgrade passed: installed the saved 2.0 APK, created a synthetic word, note, progress and chat fixture, installed 3.0 over it, and verified all records plus accessible migrated chat.
- 16 Android storage / backup / Keystore / HTTPS checks passed using an isolated validation database.
- 36 Android studio checks passed: separate materials and topic context, drafts, recordings/results, annotations, schema 2 and schema 1 backups, rollback, actual SQLite v1 migration, encrypted independent service keys, offline dictionary, actual AAC metadata and real WAV decoding, streaming UTF-8, JSON null, cancellation, JSON fallback, multipart transcription and Azure phoneme assessment request formats, and redacted errors.
- A failed AAC range-length check exposed a seek-boundary issue. The final decoder decodes 500 ms of preroll and trailing audio, crops by output presentation timestamps, and treats only sample time -1 as EOF. The original strict 3-second range check then passed; its tolerance was not relaxed.

Complete UI persistence verification did not finish. The muted emulator produced zero encoded microphone frames; real microphone recording is not marked passed.

## Evidence and limits

Current native pass logs are in `qa/upgrade-validation.txt`, `qa/legacy-storage.txt` and `qa/studio-validation.txt`. The studio protocol checks use mocked HTTP responses; they do not prove live authenticated cloud-service compatibility. No user API credentials were available.

Environment: Android 13 / API 33 AOSP x86_64 emulator, software acceleration, 360 x 780 display at 160 dpi; platform/build-tools 35.0.0; target API 33, minimum API 23. Initial emulator startup hit an Android system-server watchdog timeout before app testing. The test emulator's `ro.hw_timeout_multiplier` was increased to 10. This is a test-environment setting, not an application change or a physical-phone result.

Real phone microphone quality, TTS English packs, system recognizer availability, dedicated Bluetooth microphone routing and live service credentials have not been verified. Offline Whisper, full-duplex calling and cloud synchronization are not implemented.

APK SHA-256: `e800ce136f580adf66b27239e6aa38cb6cbb77fc9a1f7b4781d0812c83a73376`.

## Current revision

The training dialog now displays its list. Player callbacks check player/material identity; playback failures stop polling. Studio recording preparation, stop and database persistence use a serial background executor. A take captures its original material, cue, topic and dialog; an independent database transaction prevents saving against a closed activity database. A 30-second timer supplements the native recorder limit. Legacy life-English recording is unchanged.

This exact final revision passed Java/D8 compilation, APK v1/v2/v3 signatures, the same 2.0 signing certificate, 17 algorithm checks and package resources/dictionary checks. The compiled RecordingTake class was verified. Earlier native upgrade/storage/protocol logs and screenshots are preserved; those checks were not rerun on this binary. Physical-device microphone, lifecycle behavior and full UI persistence remain unverified. Cloud tests used mocks, not authenticated services.
