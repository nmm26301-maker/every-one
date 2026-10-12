# Everyone English 3.2 validation

Date: 2026-10-09, Asia/Shanghai. Version code 32 / version name 3.2.0; minimum API 23, target API 33.

## Executed host checks

99 checks passed on the new revision:

- 17 existing learning algorithms (`qa/v32-core.txt`).
- 33 text feedback, CSV/search escaping and local-day/DST boundaries; 13 exact production SQL checks in host SQLite (`qa/v32-training.txt`).
- 28 actual ZIP checks (`qa/v32-portable.txt`): binary audio bytes and UTF-8 preserved through export/extraction; native/legacy recording entries; checksum mismatch; missing/unlisted files; unknown manifests; invalid/traversal/absolute paths; duplicate manifest and ZIP entries; bounded settings expansion; cancellation; unreadable sources; entry-count limits. The 2 GB aggregate and 350 MB single-media ceilings are implemented but not exercised at full size in these tests.
- 8 CloudClient.Job cancellation/commit checks in host Java (`qa/v32-commit.txt`): normal cancellation/interruption remains effective, cancellation before commit blocks it, late cancellation/interruption cannot misreport a local commit as cancelled.

Java/D8 compilation and APK v1/v2/v3 signature checks passed. Existing Android instrumentation and the new isolated PortableValidation compile. PortableValidation is NOT executed; it covers database/file URI reconstruction, native and legacy audio mapping, original-file protection, settings/drafts, cancelled restore and exporting/restoring a second time.

## Scope and limits

The archive is copied into app-private staging and checksum-verified before an isolated SQLite validation. Installed files receive unique paths; old files remain intact. Database learning rows and the portable_state mapping are committed in one transaction. Schema/database version 2 is retained; portable_state is device-local mapping state and is not part of plain JSON backups. Late cancellation is intentionally disabled during the final transaction.

Provider-copy interruption can leave an incomplete output document. Old media generations remain after restore, and a process death before commit can leave unused staged/installed files. These do not replace prior active database mappings, but use storage. Archive checksums detect content damage; they are not signatures or encryption. API credentials are not exported.

No Android instrumentation, emulator UI, physical-phone microphone, system document-picker/cloud-provider round trip, authenticated cloud service, TTS pack or Bluetooth microphone was tested on 3.2. Earlier 3.0 Android logs and 3.1 host records remain historical evidence only. Offline Whisper, accounts/cloud synchronization and full-duplex calling are still unimplemented.

## Final artifact

Package resources verified: DEX, 60 lessons, 10 book chapters, synthetic audio and 10 cues, 20,000 dictionary entries/forms and licenses. Manifest version 3.2.0/code 32 confirmed. APK certificate equals the prior builds. SHA-256: `586ee997641969a58202c2594599ab4f104e400f786d9a35b25f24feecfabfc4`.
