# AI engine error boundary · 5.0.22

Baseline: 1409c9a21633ec07d3d9ccd29175b0108683ab83 / 5.0.21.

## Findings and scope

No handset crash trace was supplied. Static inspection found that JourneyActivity action/worker boundaries caught Exception, which excludes LinkageError. A missing/incompatible native engine or binary method could therefore terminate the process rather than follow the normal failure path. This is a verified code gap, not a confirmed explanation of the reported AI-entry crash.

JourneyCore.engineCall converts LinkageError (including UnsatisfiedLinkError, NoSuchMethodError, NoClassDefFoundError and initialization errors) into an actionable IOException with its cause retained. UI actions and work execution use this boundary; worker failures follow the existing token check, finishJob cleanup and retained-question retry path. Task construction now occurs before busy/keep-awake flags are changed. VM errors, native signals and OS termination are intentionally outside this boundary.

The latest ordinary task/action failure is saved separately from learning exports. Diagnostics include at most three distinct cause types and six stack locations per cause, bounded to 3000 characters. Exception messages, stack filename paths and request/user text are omitted. Settings provides explicit view/copy/clear controls; copying includes app version only. No automatic transmission is added.

## Executed validation

23 engine-boundary/diagnostic checks passed, plus 56 learning, 30 retry, 52 draft, 158 backup and 75 page checks. Tests cover converted linkage errors, exact ordinary/cancellation exception propagation, VM-error propagation, bounded/cyclic cause chains and omission of private messages/paths. Curriculum: 48 units / 144 utterances. git diff --check passed.

The complete APK builder compiles actual Activity code and now replaces the JourneyCore family as well as the established Activity/PhoneEngine/State families. It verifies 907 method contracts, preserves 1143 unrelated classes and all 353 bundled resource/model/media entries. Signed package evidence is in release-5.0.22.json.

## Unverified behavior

Original handset crash, real ARM model execution, real service interaction and physical installation remain unverified. Android 13 x86_64 software-emulator checks completed: five baseline UI checks (cold launch, conversation entry, send control, unsupported-engine failure and retry retained) and 29 updated instrumentation assertions passed. The updated run injected an UnsatisfiedLinkError through the real private worker interface and verified visible error text, task/keep-awake cleanup, unchanged pending conversation, a subsequent successful task, diagnostic privacy, clipboard copy and clear. Full ARM APK installation was rejected by the x86 image. Disposable test-signed copies omitted ARM libraries and assets larger than 10 MB, with identical DEX, manifest, resources and curriculum bytes confirmed. This tests ART/Activity integration and injected errors, not the actual native engine. Evidence: runtime-probe-5.0.22.json. Host tests do not prove Android UI/job cleanup integration or recovery from native SIGSEGV, OOM or process kills. This release must not be reported as a confirmed fix for the original crash without reproduction evidence.
