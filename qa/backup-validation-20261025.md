# Learning backup validation · 5.0.20

Baseline: 52186edf965e3bc6ddd66a332aff12d20f37b4bd / 5.0.19.

## Changes

Backup schema/level, progress bitmasks, review stages, counters and timestamps require actual bounded integers, not JSON strings, fractions, booleans or nulls. Optional recall/assessment flags must be booleans; optional feedback and chat contents must be strings of at most 1500 UTF-16 units. Legacy optional fields may remain absent. Valid course/card identifiers and existing size limits remain enforced.

State mutators reject invalid scene IDs, sentence indexes, progress fields/bits, roles and null content before changing the journal. Attempt counts saturate at Integer.MAX_VALUE; recall timestamps must leave room for the longest 30-day interval.

## Executed

Production JourneyState host suite: 158 new backup checks plus 56 learning, 30 retry and 52 draft checks passed. Includes malformed backup types, unchanged state after rejection, reopening after rejected writes, legacy missing fields, maximum counter saturation and distant valid due dates. Curriculum: 48 units / 144 utterances. git diff --check passed.

The release builder compiles all Journey sources, replaces only the established Activity/PhoneEngine/State families, verifies 849 runtime method contracts, and checks 1144 unrelated classes and 353 bundled entries against the original-signed 5.0.19. Signing/recovery evidence is in release-5.0.20.json.

## Not executed

No physical handset installation, real Android storage-failure injection, UI interaction, cloud-service request or audio-quality test. Host preferences fixtures verify state boundaries; they do not prove disk durability under power loss. Integer-looking JSON strings/floats previously tolerated in hand-edited imports are now rejected; the app's own integer exports remain supported.
