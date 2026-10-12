# Everyone English 3.3 validation

Date: 2026-10-09 UTC. Version code 33 / version name 3.3.0; minimum API 23, target API 33.

## Executed host checks

124 checks passed on this revision:

- 17 learning algorithms (`qa/v33-core.txt`).
- 33 text feedback, CSV/search escaping and local-day/DST boundaries; 13 exact production SQL checks in host SQLite (`qa/v33-training.txt`).
- 28 actual ZIP round-trip, binary/UTF-8 integrity, unsafe/duplicate/missing entries, bounds and cancellation checks (`qa/v33-portable.txt`). The implemented 2 GB aggregate and 350 MB single-media ceilings were not exercised at full size.
- 8 CloudClient.Job cancellation and final-local-commit checks in host Java (`qa/v33-commit.txt`).
- 14 review scoring and subtitle-identity checks (`qa/v33-review.txt`): exact/current and relocated anchors, duplicate/missing/edited subtitles, normalized correct writing, missing words, no English reference, bounded work, long identical input and a rounding regression where one missing word previously could round to 100%.
- 11 exact production review-query checks in host SQLite (`qa/v33-review.txt`): newest timestamp rather than greatest ID, same-time ID tie, correct latest attempt replacing old errors, empty attempts, audio recognition, independent materials and original sentences, changed anchors, non-media exclusion and deletion revealing the newest remaining record. SQL selection and host scoring are tested separately; the Android cursor-to-JSON integration was not executed.

Java/D8 compilation and APK v1/v2/v3 signatures passed. Asset checks confirm DEX, 60 lessons, 10 book chapters, synthetic demo audio/10 cues, 20,000 dictionary entries/forms and licenses. Manifest and certificate evidence: `qa/v33-manifest.txt`, `qa/v33-certificate.txt`. Android instrumentation compiled (`qa/v33-instrumentation-compile.txt`) but was NOT executed. Earlier 3.0 Android runtime/upgrade logs and 3.1/3.2 reports are historical evidence only.

## Resulting behavior

The review queue derives from the latest nonempty dictation per material and original text. Audio recognition is excluded. Normalized exact word equality is the only route to 100%; unequal bounded comparisons are capped at 99%, preventing rounding from clearing a missed word. Over 40,000 word-pair operations or no English reference produces an ungraded/manual-check state, not a fabricated percentage. Details, dictation, recorded transcription feedback and CSV share the work bound; ungraded CSV percentages are blank.

Current cue identity is checked before a review appears and again on opening. An exact old anchor is preferred; relocation requires a unique unchanged original. Edited or ambiguously relocated originals are skipped. Queue pages retain at most 30 records and an eight-material cue cache while scanning in the background. Identical original text within a material is intentionally one review item. Deleting the newest attempt exposes the newest remaining attempt; deleting a material removes its queue entries. No database version or backup-format change is needed.

Review cards omit answers. The player body, including subtitle buttons, is made invisible while the review dictation dialog is open; it becomes visible on check/cancel. This UI behavior was checked in source and compilation, not by Android UI automation.

## Portable-backup scope and limits retained from 3.2

The portable ZIP includes learning data, referenced media and recordings, legacy recordings and whitelisted local preferences. API credentials and connection settings are excluded. Archives are staged and checksum-verified, then JSON is validated in an isolated SQLite database. New media paths protect old files; live learning rows and local media mappings commit together. Late cancellation is disabled only for the final local transaction. Preferences apply after commit and recover once on next startup if needed.

Provider-copy interruption may leave an incomplete output document. Old media generations remain; a process death before commit can leave unused staged/installed files. Checksums are damage detection, not signatures or encryption. Plain JSON does not contain media. Full-device restore, provider-picker round trips and Android SQLite integration were not executed on this revision.

## Remaining validation and features

No emulator UI, physical-phone microphone, TTS pack, Bluetooth microphone, system document picker/cloud-provider round trip or authenticated cloud service was tested on 3.3. Native instrumentation is only compiled. Offline Whisper, offline large models, accounts/automatic cloud synchronization, full-duplex calling, course marketplace and community remain unimplemented. Classic legacy lesson recording still uses its older synchronous path; background recording improvements apply to Studio material/topic recording.

## Final artifact

APK certificate SHA-256: `716c06c3b5a7c4dc8a3a34c2baf445f56d62f075d91ab864156dc5ce48e88887`, identical to previous deliveries. APK SHA-256: `14dfe45a6291b27932b279b6c916d757d4e70d6981c10384c61a27e33c16dd74`.
