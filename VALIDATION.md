# 4.6.1 validation

18 original bilingual stories, 54 paragraph pairs and 90 contextual vocabulary cards. ReadingProgress tests cover daily rollover, 10-minute completion, duplicate completion, position/card serialization, bounded input and spaced repetition (24 checks). All 18 content records verified.

Existing host regression suites were rerun; TIFA's 1784 real host ONNX checks passed, including 204 reference courses, cancellation and decoder agreement. No learner pronunciation accuracy claim. New reading audio uses the unchanged bundled eSpeak native library as fallback; no new real-native synthesis or device TTS execution was performed. Android Java/DEX/resources compile, signer continuity and package/ZIP alignment verified.

No Android phone/emulator available: physical installation/upgrade, UI interactions, microphone, system TTS callbacks, actual playback and speed/timing on Mi 10 Pro remain unexecuted. Reading UI and new audio lifecycle are compiled and reviewed, not device-instrumented.

Historical evidence follows.

# 4.5 validation

VersionCode 45 / versionName 4.5.0, org.openenglish.reader, minSDK23 target33 ARM64.
The new TIFA runtime requires SDK24 and is gated before initialization. Signer preserved from 4.4.

1784 new checks execute the production LocalTifa, TifaDecoder, SegmentCoach and ArticulationHints with official host ONNX Runtime 1.22.0. They include 200 canonical Python decoder fixtures and all 204 real reference-audio Java model runs. All token boundaries match Python ONNX Runtime 1.31.0 reference inference (maximum drift 0 frames). Long Japanese vowels combine for playback; sokuon retains its own practice unit. Cancellation terminates a pending run and a subsequent run succeeds. Input, crop, replay ranges and unavailable reference-phone behavior are checked.

670 prior host regression checks rerun (qa/v45-test-*.txt). Whisper/LLM/native speech engines and old learning materials are unchanged. This release does not add a new chat/ASR latency benchmark.

One existing reference (dojo_w_wan2) has a skipped phone; its group is marked unavailable for sliced replay/focused recording instead of treating alignment as accurate. Known-template alignment can still be wrong, including on silence or a different spoken word. Acoustic quality is checked before analysis; no evidence of calibrated pronunciation grading is implied by alignment.

Research candidate-margin test on bundled references: zh_nian/zh_lian [3.965, 0.142], zh_zhi/zh_zi [7.668,-5.902], zh_chi/zh_ci [7.637,-7.258], zh_shi/zh_si [6.243,-4.579]. Positive favours the first template. n/l did not reverse direction on the contrast reference; no candidate classifier is exposed or used by this build. None of these small native/synthetic-reference checks validates L2 learner grading.

No Android phone/emulator execution, on-device timing/frame-rate measurements, learner error corpus evaluation, teacher-labelled pronunciation grading, upgrade trial, microphone/TTS/lifecycle UI run. Unit recordings are temporary and do not overwrite the backed-up complete take; stars reward complete practice, not correctness. Android UI behavior is implemented and compiled but not instrumented on a device.

Licenses: TIFA code MIT; original model and ONNX/template/reference alignment adaptations CC BY-NC-SA4.0; ONNX Runtime MIT. Source includes pinned original TIFA code and rebuild script. No zero-delay or fully reliable consonant/vowel correction claim.

Historical 4.4 evidence follows:

# 4.4 validation

VersionCode 44, versionName 4.4.0, org.openenglish.reader, minSDK 23/target 33, arm64. Original signing certificate SHA-256: 716c06c3b5a7c4dc8a3a34c2baf445f56d62f075d91ab864156dc5ce48e88887.

## Executed on host: 678 checks

637 existing checks rerun in qa/v44-test-*.txt, including scene isolation / SpeechDeadline, backup, learning, review, portable ZIP, voice-turn invalidation, playback protection, microphone / recorder fixtures, acoustic resources, model installer and 4.3 waiting / preparation controllers.

33 new checks in qa/v44-test-completion.txt execute production TranscriptCheck, IslandScene, IslandMemory and LocalAI Java. They verify Unicode-safe bounded text alignment, orthography caveats, missing / inserted spans, short-word exclusion, exact scene filtering, search of both participants, language isolation, durable reopening, deletion, capacity, multilingual ASR language routes without installed teacher, silent / cancelled / empty recognition failures. Android preferences and native ASR are recording fixtures; this does not prove recognition accuracy.

8 new checks in qa/v44-test-real-speech.txt execute actual rebuilt host libenglish_speech.so. They produce PCM signals for en-us/en/yue/ja/cmn, exercise cancellation and unsupported language rejection, and synthesize again after those failures. Speech bridge code matches the Android rebuild. This proves host native synthesis, not handset playback, naturalness or pronunciation grading.

## Build and package evidence

qa/v44-apk-build.txt, v44-android-speech-build.txt, v44-host-speech-build.txt, v44-native-comparison.txt, v44-signature.txt, v44-align.txt, v44-badging.txt and v44-package.txt. Java/D8/assets/resources build and original signature pass; ARM64 speech JNI rebuilt with NDK r27c and 16 KiB ELF alignment, ZIP alignment checked. Whisper/llama/libc++ and model assets are unchanged from 4.3. The package validates all 204 cards, 199 distinct reference recordings and their explicit five legacy duplicates, model SHA-256s, licenses, 60 English lessons, book/audio/cues and 20,000 dictionary entries.

## Not executed on handset

No phone/emulator available. Actual APK upgrade, UI/rotation interaction, TTS/MediaPlayer callback recovery, audio routing, microphone, full duplex echo, SAF, and Mi 10 Pro latency have not been run. The preview is an illustrative drawing of the native interface. No zero latency or full phoneme-scoring claim. Only production Java ASR routing is newly tested; no new real Whisper or LLM inference benchmark.

The original English page uses DeepSeek/tiny.en, while Magic Island uses its selected teacher/multilingual Whisper. Its speech recovery now has an English native fallback; on system failure the current chunk may repeat before the remaining queue continues. Cross-store backup limitations remain unchanged.

---

Historical evidence follows:

# 4.3 validation

VersionCode 43, versionName 4.3.0, org.openenglish.reader, minSDK 23/target 33, arm64. Java/D8/resources build, signature, package assets and 16 KiB ZIP alignment are checked. Original certificate SHA-256: 716c06c3b5a7c4dc8a3a34c2baf445f56d62f075d91ab864156dc5ce48e88887.

## Executed on host: 637 checks

30 new checks execute the production LocalAI Java warmup routing, WorkBudget and PracticePath. Native/model methods use a recording test substitute. They verify text skips Whisper/VAD, voice keeps ordered preparation, cancellation before/between preparation, selected-model routing, unsupported ABI, total/idle deadlines, completed-result boundaries, duplicate-output limits, explicit-rating path progression, repeated callbacks and restored cursor. These are control-flow checks, not real model timing or performance measurements.

The prior 607 regression checks were rerun: 16 scene/speech-deadline, 19 Magic backup round-trip/retry/input validation, 17 learning, 46 training, 28 portable ZIP, 25 review, 73 planner, 25 island SRS/prompt/chunks, 12 memory/review, 39 voice flow, 15 playback guard, 8 commit/cancel, 15 threaded microphone fixture, 22 recorder fixture, 233 acoustic/resource and 14 model-installer checks. Logs are qa/v43-test-*.txt and qa/v43-voiceflow.txt.

Host preferences are a typed durable fixture, not Android SharedPreferences. SQLite fixtures run host SQLite, not Android SQLite. The new session stores its transient cursor in Activity state; host cursor tests do not prove Android lifecycle ordering or process-death restore behavior.

## Build and package

qa/v43-build.txt, v43-final-java.txt, v43-signature.txt, v43-align.txt, v43-badging.txt, v43-package.txt and v43-instrumentation-compile.txt contain evidence. Android instrumentation is compiled and packaged, not executed. Asset checks cover DEX, all four arm64 libraries, bundled Whisper/VAD model hashes, 204 lesson/reference hashes, 199 distinct recordings, licenses, 60 English lessons, book/audio/cues and the 20,000-entry dictionary. Five original starter cards retain IDs and share audio with their expanded equivalents; the explicit duplicate mapping remains checked.

Native binaries and model assets remain unchanged from 4.2. No new native inference/handset benchmark was run. Removing text-mode speech warmup and ASR-queue dispatch removes unnecessary work, but no millisecond improvement is claimed. All waiting limits use monotonic elapsed time and a UI tick of 1 second; native cancellation is cooperative and cannot force immediate termination of every blocking loader call.

## Not executed on handset

No connected Android phone or emulator. Actual APK upgrade, UI interaction, Bluetooth routing, microphone/audio capture, full-duplex echo, TTS/MediaPlayer callbacks, SAF restoration and real Mi 10 Pro delay are not measured. The drawn preview shows illustrative ready/completed/timeout states and is not a device screenshot. No zero-delay or precise phoneme-scoring guarantee is made.

The completed conversation and Magic backup format remain 4.2 compatible. Existing database/preference restore limitations remain described in README-4.2.md; no cross-store atomic-transaction claim is made.

---

Earlier-version evidence (historical, not new 4.3 native execution):

# 4.2 validation

VersionCode 42, versionName 4.2.0, org.openenglish.reader, minSDK 23/target 33, arm64. Full Java/D8/resource build and original signing key verified. Certificate SHA-256: 716c06c3b5a7c4dc8a3a34c2baf445f56d62f075d91ab864156dc5ce48e88887. APK 16 KiB zip alignment passes. All native binaries and model assets are unchanged from 4.1; no new native-model benchmark was run for 4.2.

## Executed on the host: 607 checks

- Scene continuation and speech callback/deadline ownership: 16.
- Magic preference, memory, review and WAV archive round trip, failed commit retry, re-export and invalid inputs: 19.
- Core learning: 17; training: 33 Java + 13 host SQLite; portable ZIP: 28; review: 14 Java + 11 host SQLite; planner: 32 Java + 41 host SQLite.
- Island SRS, prompt, CJK streaming chunks: 25; durable memory/review: 12.
- Voice flow: 39; playback guard: 15; cancellation/commit: 8; threaded microphone fixture: 15; threaded lesson recorder fixture: 22.
- Acoustic algorithms and all 204 bounded reference clips: 233; model installer: 14, including bundled multilingual model checksum/extraction.

The new preference fixture implements typed, durable host storage with injected commit failure. It does not emulate Android SharedPreferences scheduling or filesystem power-loss behavior. SpeechDeadline checks the exact controller used by MagicDuplexActivity; Android system TTS/MediaPlayer callback ordering and real audio output were not exercised on a handset.

## Build and asset verification

qa/v42-build.txt, v42-signature.txt, v42-align.txt, v42-badging.txt and v42-package.txt contain current evidence. Package verification checks DEX, all four arm64 libraries, all bundled model checksums, all 204 lesson/audio hashes, 199 distinct clips, licenses, English lessons/book/media, dictionary forms and 20,000 words. Five legacy starter lesson cards and expanded cards share the same text/reading/audio; their IDs are preserved for existing learning progress. This is intentional and is asserted explicitly.

All Android instrumentation sources compile against platform 35 and current application classes. PortableValidation now includes magic progress, scene memory, SRS, WAV restoration, re-export and a pre-4.2 package preserving current Magic data. Compilation is not execution: no Android device or emulator is connected, and this integration suite was not run.

## Limits

No measured Mi 10 Pro latency, full-duplex echo quality, Bluetooth routing, system TTS behavior, real microphone capture, SAF provider write, APK installation or cross-version upgrade was verified on a device. Signature/package continuity permits in-place upgrade but does not replace an installation test. No claim of zero delay, precise phoneme grading, or complete resolution of the user's phone symptom is made. This release adds deterministic recovery and durable-scene logic; real audio still needs handset testing.

Portable restore validates before DB commit, then applies three preference stores with a last completion marker and startup retry. It is not an atomic transaction across SQLite and preferences; storage failure after database commit leaves a pending migration and a visible retry message. Old installed media directories are retained so an old package restore cannot break current Magic take mappings.

The preview PNG is drawn from the native layout, not a device screenshot. Earlier 4.1 native/model test results below are historical, unchanged-engine evidence, not new 4.2 execution.

---

# Everyone 4.1 validation — 2026-10-11

This is an ARM64 Android APK built and signed locally. No connected Android handset or emulator was available. UI, acoustic echo cancellation and end-to-end phone latency have NOT been certified by these checks.

## Current checks (not historical v4.0 evidence)

578 host checks passed:

| Suite | Checks | Evidence |
|---|---:|---|
| Learning core | 17 | qa/v41-core.txt |
| Training and production SQLite queries | 46 | qa/v41-training.txt |
| Portable ZIP boundaries | 28 | qa/v41-portable.txt |
| Review scoring and SQLite queries | 25 | qa/v41-review.txt |
| Scheduling and production SQLite transactions | 73 | qa/v41-planner.txt |
| Voice onset, endpoints, chunking, turn cancellation | 39 | qa/v41-voice-flow.txt |
| Playback echo guard and diagnostics | 15 | qa/v41-playback-guard.txt |
| Cancellation/commit boundary | 8 | qa/v41-commit.txt |
| Real bundled extraction/hashes and installer HTTP/provider fixtures | 14 | qa/v41-model-install.txt |
| Microphone lifecycle, routing, manual input — Android API fixtures | 15 | qa/v41-mic-capture.txt |
| Acoustic analysis, WAV round trips and all 204 reference files | 233 | qa/v41-acoustic.txt |
| Lesson capture — Android API fixtures | 22 | qa/v41-lesson-recorder.txt |
| SM-2, CJK streaming and prompt boundaries | 25 | qa/v41-island-tests.txt |
| Durable preferences/complete-pair memory, isolation, clear and cap | 12 | qa/v41-island-tests.txt |
| Kanji reading from actual asset dictionaries, then real native speech | 6 | qa/v41-japanese-reading.txt |

73 real host JNI/model checks passed:

- 20: multilingual Whisper base decoding with specified language routes; English-only model rejection for Japanese; original English decoding; Qwen 0.5B streamed generation/cache reuse; real eSpeak Yue/JA/CMN PCM output and cancellation. `qa/v41-native-inference.txt`.
- 21: real Qwen 1.5B and DeepSeek 1.5B generation/stream completion/cache reuse; active generation cancellation. `qa/v41-dialogue-models.txt`.
- 18 + 14: original English Whisper/DeepSeek grammatical correction, model failure and cancellation; actual Silero speech/no-speech; ASR concurrent with generation; cache lifetime and cancellation. `qa/v41-english-native.txt`, `qa/v41-duplex-native.txt`.

Model tests establish integration and cancellation, NOT teacher-quality correctness across arbitrary speech. In the Cantonese fixture Whisper produced `早晨,吃了飯味啊?` for `早晨！食咗飯未呀？`. Japanese and Mandarin coffee fixtures were recognized correctly. Updated Qwen 1.5B tutor prompt produced `好呀，少甜定正常甜呀？` for a Cantonese cafe request. DeepSeek sometimes merely repeated the learner or mixed English into Japanese. The app recommends Qwen and explains these limitations.

The 164 new Jyut Dojo clips all decode and have bounded analysis. Very short/unvoiced clips can be unsuitable for pitch comparison and are not automatically treated as correct pronunciation. The original 40 reference clips remain usable in the bounded YIN tests. A recording score does not prove the right word, consonant or vowel was pronounced.

## Build/package validation

- All production Java and dependencies compiled, converted to DEX, packaged and APK signature v1/v2/v3 verified.
- Android instrumentation sources compiled into a separate validation APK. They were NOT run on a device.
- All four ARM64 libraries were built with NDK r27c; ELF LOAD alignments at least 16 KB; native APK entries are stored and 16 KB zip alignment checked.
- Package version 41 / 4.1.0, org.openenglish.reader. Signer SHA-256 remains `716c06c3b5a7c4dc8a3a34c2baf445f56d62f075d91ab864156dc5ce48e88887`, compatible with the prior local release.
- APK assets include the pinned/hash-verified Whisper base, existing English tiny.en and Silero; 204 WAV lesson references; eSpeak data; Japanese IPADIC assets; third-party notices. Dialogue GGUF models require an explicit download/import.
- Preview is code-drawn from the native layout, not a device screenshot.

## Real-phone checks still needed

1. Cover-install on Mi 10 Pro; verify old English data and new magic memory across restart.
2. Select/download Qwen; test typed cafe dialogue in each language; test ASR corrections by tapping recognized text.
3. Test sustained multi-turn voice input on the actual microphone, especially quiet speech and short Yue responses.
4. With a wired/USB/Bluetooth headset, interrupt during generation and speech; ensure old callbacks cannot resume the interrupted turn.
5. With speaker output, verify default echo guard avoids self-trigger. Experimental speaker barge-in remains device-dependent; manual hold-to-talk remains available.
6. Force a system TTS stop/error and missing language voice. New magic dialogue has recovery to eSpeak and a start/finish watchdog, but this system failure path has not been executed on Android hardware here.
7. Confirm Japanese kanji is pronounced with asset-based Kuromoji readings in fallback mode; install a natural offline system voice for better sound.
8. Measure displayed ASR, first-text and speech-start times under phone load. No zero-latency or no-stutter guarantee is made.

All older qa/v3*-v40 files in this source bundle are historical evidence and must not be counted as current handset validation.
