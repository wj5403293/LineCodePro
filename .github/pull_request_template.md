## Summary

<!-- What does this PR do and why? Link the issue if any. -->

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that changes existing behavior)
- [ ] Refactor / code quality (no behavior change)
- [ ] Docs / changelog only

## Scope

<!-- Which areas are affected? e.g. chat UI, tool-call loop, agent, model protocol, settings, SSH/terminal provider. -->

## Changes

<!-- Bullet the concrete changes. Reference classes/methods where helpful. -->

-

## Testing

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintDebug` passes
- [ ] `./gradlew :app:assembleDebug` builds
- [ ] Manual verification on device/emulator (describe what was checked)

## Compliance checklist

- [ ] Java only in `app/src/main/java` (no Kotlin sources added)
- [ ] UI built in Java code, not inflated from XML layouts
- [ ] New secrets/redaction fields covered by `ErrorLogRedactor` / `ArchiveSecretRedactor`
- [ ] State threaded through `ChatUiStateAssembler` → `ChatUiState` → `MainContract.View.render(...)` (no direct view reach-in)
- [ ] `update.md` updated for user-facing changes

## Risk / rollback

<!-- What could break, and how to revert? -->
