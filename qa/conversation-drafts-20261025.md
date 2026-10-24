# Conversation drafts · 5.0.19

Source baseline: 55ebf4d90ac0736a88c84c6081425cf41fbb2e97 (5.0.18).

## Behavior

- Save drafts by scene on page changes and Activity pause. The scene attached to the existing input is captured before a newly selected lesson is rendered.
- Commit the outgoing question and consuming its matching draft together. Empty/oversized submissions are rejected before journal mutation; failed commits roll back the journal and leave the input visible.
- Preserve another typed draft when sending a helper prompt, clearing chat history, retrying or receiving a reply.
- On storage errors, retain failed drafts in this Activity and show a warning. Retry them on later navigation/pause; do not export a backup while drafts are unsaved. This cannot guarantee persistence across process termination while storage is failing.
- Include drafts in learning exports, accept old schema-1 backups without drafts, validate optional draft objects/scene IDs/string types/1500-character limits, and clear transient draft caches only after successful restore.
- Input uses Android's 1500 UTF-16-unit LengthFilter. The journal preserves exact accepted text and whitespace; only the sent question is trimmed.

## Executed validation

`JOURNEY_JSON_JAR=.../deps/json.jar bash tests/test-journey.sh` passed: 56 learning checks, 30 retry checks, 52 new draft checks, and 48 units / 144 utterances.

The new suite exercises actual JourneyState persistence: separate scenes, reopening, backup round trips, old backups, maximum sizes, malformed imports, atomic send, failed/throwing storage, recovery, Unicode, helper prompts, and drafts retained after responses or clear-chat.

`bash tests/test-conversation-boundary.sh` passed: 24 conversation-boundary, 39 voice-flow and 15 playback-guard checks. `git diff --check` passed.

The release builder compiles JourneyActivity and validates preserved bytecode/resources against the original-signed 5.0.18 APK. Signed package evidence is recorded separately in `qa/release-5.0.19.json` after the build.

## Not executed

No physical Android installation, navigation/rotation interaction, real cloud requests or audio-quality test was performed. Host persistence tests do not prove Activity lifecycle behavior on a device. Draft saving on pause/navigation does not promise recovery after an abrupt process termination before those callbacks.
