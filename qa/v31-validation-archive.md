# Everyone English 3.1 validation

Date: 2026-10-09, Asia/Shanghai. Minimum Android API 23, target API 33, versionCode 31 / versionName 3.1.0.

## Checks run on this revision

- Java compilation and D8 complete; APK signature and exact artifact checks are recorded after the final build below.
- 17 existing learning-core checks passed (`qa/v31-core.txt`).
- 33 training-core checks passed (`qa/v31-training-tests.txt`): missing/replaced/extra/repeated words, curly apostrophes, punctuation and case normalization, bounded alignment, literal search characters, CSV quoting and formula-like text, seven adjacent local date windows, year boundaries, 23-hour and 25-hour DST days.
- 13 host SQLite checks executed the exact `StudioStore` SQL constants: bound search parameters, literal %, _, !, audio/dictation classification, ordered pagination, material/cue/reference de-duplication, half-open day boundaries, and deleting one versus the final repeated attempt. These are host SQLite checks, not Android SQLite instrumentation.

The percent-search fixture initially expected 4 matches; the query correctly returned 5 because the title matched four rows and the literal reference matched another row. The fixture was corrected after inspecting the records; query escaping was retained.

## Compatibility and scope

The database remains version 2 and backup schema 2. New indexes are created on open; no learning tables are replaced. Existing signing key is retained. Daily goals and playback preferences are local settings and are not included in learning JSON.

Earlier Android upgrade, 16 storage/Keystore and 36 Studio/mock-protocol checks are preserved in `qa/upgrade-validation.txt`, `qa/legacy-storage.txt`, `qa/studio-validation.txt`. Earlier 3.0 screenshots and the archived report are clearly prior-version evidence. Those checks were not rerun on 3.1.

No emulator UI, physical-phone microphone, real cloud credentials, TTS pack, Bluetooth microphone or full learning-flow validation was run on this revision. The earlier muted emulator produced zero encoded microphone frames; successful recording is not claimed. Offline Whisper and account/cloud synchronization remain unimplemented.

## Final artifact

Final APK signatures v1/v2/v3 passed; certificate matches 2.0/3.0. Manifest versionCode 31 and versionName 3.1.0 confirmed. DEX includes TrainingCore. Package resource check passed (60 lessons, 10 book chapters, 10 synthetic audio cues, 20,000 dictionary entries and licenses). The existing Android instrumentation package compiles; it was not executed. Logs: `qa/v31-build.txt`, `qa/v31-package.txt`, `qa/v31-artifact.txt`, `qa/v31-instrumentation-compile.txt`.

SHA-256: `f66884fba2fcaf02c2c7cb6c8197c612544dfbfb1de87867946542fa6a57eeb1`.
