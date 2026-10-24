# Everyone 5.0.18 conversation retry

Base: `5fcb8ca626e61da62223bf62fa25ef6a198f6914`.

Changes:

- A scene whose last saved message belongs to the user exposes a retry action. Retrying reuses the saved history and does not add another user message. The pending message already survives reopening and backup through the existing journal format.
- Empty/whitespace replies fail visibly without adding an assistant row. Reply persistence checks that the expected question is still pending. Cleared scenes, different questions and completed replies reject stale/duplicate responses.
- Refreshing a conversation after an AI response retains the currently typed draft. Sending clears the input only when it matches the submitted message; helper prompts do not erase a different draft.

Executed: 30 production-state retry checks, the existing 56 learning-state checks and 48-unit/144-utterance curriculum validation. Checks cover pending-state persistence, backup, response identity, duplicate responses, intentionally repeated user words, scene isolation, empty responses, clear, storage failure and recovery.

The full APK builder verifies Android Java/DEX compilation, runtime method contracts, preservation of unrelated classes and all existing model/media assets. Signed package evidence is in `release-5.0.18.json`.

Not executed: Android UI interactions for draft retention/retry, real phone installation, real microphone/model performance or live cloud response accuracy. Host tests do not certify those behaviors.
