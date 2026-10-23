# Everyone 5.0.17 download recovery

Base source: `8a75814d46de088bfe753a550f7c3b16dd3672af`.

The new production-downloader fixture reproduced a 5.0.16 failure: throwing from HTTP disconnect exited the download finalizer before the global task lock was released. A subsequent install/assessment could remain busy. The patched finalizer releases the connection asynchronously, always clears task ownership and partial files, and does not reject a successfully verified installation because teardown failed.

Cancellation closes the stream and disconnects the connection independently. A blocked disconnect no longer prevents the stream close from starting. Copy loops check cancellation before and after provider reads, so a cancellation reported by the progress callback does not trigger another read. Cancellation already present before download does not reserve a task or open a response.

Executed:

- 24 new production download checks using deterministic HTTPS connection and file-provider fixtures: teardown failure and retry, HTTP 403/404/500, checksum rejection preserving the installed model, valid HTTPS redirect, HTTP downgrade rejection, independent cancellation cleanup, partial-file cleanup and no read after cancellation.
- Existing 25 import/cancellation checks, 56 learning-state checks, 31 phone compatibility checks and 48-unit/144-utterance curriculum validation passed.
- Complete Android Java/DEX/package verification is recorded in `release-5.0.17.json` after signed packaging.

These host fixtures do not measure real Android HTTP providers, microphone behavior, phone installation or acoustic inference performance.
