# Activity page recovery · 5.0.21

Baseline: eae8ee3dfa4f41ad62885d3d504ece706b3af54e / 5.0.20.

## Behavior

JourneyActivity saves a small typed page descriptor in Android instance state, after attempting to persist conversation drafts. Recreation dispatches to the selected course, sentence, review/conversation page, word drill or global page. Listening now records its active sentence index. Existing saved drafts and pending messages are read from the journal; navigation decoding does not mutate progress or append messages.

The descriptor is separate from exported learning data and contains no credentials, chat text, audio, active job or network handles. Unknown pages, missing required scene IDs, wrong types, invalid indexes and oversized descriptors fall back to home. Review mode is restored only on the practice page; a word target is retained only for drills.

## Executed validation

75 production-descriptor checks passed: all 12 page types, first/last scenes, all listening indexes, review/word normalization, malformed/missing/oversized data, old instances with no descriptor, and no journal side effects. Existing 56 learning, 30 retry, 52 draft and 158 backup checks passed. Curriculum: 48 units / 144 utterances. git diff --check passed. SDK-tool owner execution permission recovery was also exercised with a non-executable temporary aapt2 launcher and an already executable launcher.

The release builder compiles the real Activity and verifies class/method compatibility plus unchanged bundled media/models against original-signed 5.0.20. Signed package/recovery evidence is in release-5.0.21.json.

## Limits and unexecuted checks

No physical handset rotation, process-recreation UI test, Android installation, service request or audio-quality test was executed. Host descriptor tests do not prove Android lifecycle integration. Android must supply saved instance state; fresh launch, force-stop or task dismissal is not promised to reopen the page. Playback, recording and network work require explicit restart. Temporary recordings, scroll position, quiz option order, revealed-answer toggle and unsaved service-settings fields are not restored. A storage error can still prevent draft persistence; the existing warning remains.
