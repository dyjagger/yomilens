<!-- adopt-multi-agent-dev:start -->
## Multi-agent development workflow

### Project purpose
- Goal: Build an Android prototype that reads Japanese text through the camera and lets the user view furigana, romaji, or English.
- Primary users/operators: Inferred from the requested features—Android users who are learning or reading Japanese.
- Lifecycle stage: Prototype.
- Canonical evidence: The user's 2026-08-06 project request, `README.md`, and `docs/ARCHITECTURE.md`.
- Unresolved: Product name, release branding, distribution, and release-signing requirements are not yet specified.

### Orchestration
- The main thread owns the plan, shared state, integration, and final response.
- Delegate only concrete, bounded, independently useful work.
- Prefer parallel read-only discovery; assign one writer per overlapping area.
- Wait for required agent results and reconcile them against repository evidence.

### Agent selection
- Start in the main thread. Spawn only the agents justified by independent workstreams.
- Use the built-in explorer for read-heavy discovery and worker for assigned implementation.
- Use `reviewer` after material changes or for high-risk analysis.
- Project concurrency cap: 2 spawned threads because this is a new, single-application Android prototype with one overlapping implementation surface and one independent review lane.
- Conditional roles: None yet; reassess after the first working camera/OCR pilot.
- Archivist: Not warranted because the repository is new and empty, and no cross-session continuity requirement is established yet.

### Ownership and result contract
- Do not let agents edit the same files concurrently without isolated worktrees and explicit ownership.
- Workers return: result, evidence, files touched, checks run, confidence, and open questions.
- Reviewers return: PASS, REPAIR, REPLAN, or ESCALATE with exact evidence.

### Verification and stopping
- Run `./gradlew testDebugUnitTest --no-parallel` for local reading tests.
- Run `./gradlew lintDebug --no-parallel` for Android static analysis.
- Run `./gradlew assembleDebug --no-parallel` for a debug APK.
- Run `./gradlew compileDebugAndroidTestKotlin --no-parallel` when no stable emulator or device is available; run `./gradlew connectedDebugAndroidTest --no-parallel` on a stable device before release.
- Limit review/repair to two loops.
- Stop when acceptance criteria pass, the loop limit or budget is reached, or user input/permission is required.
- Preserve unrelated and pre-existing changes.
<!-- adopt-multi-agent-dev:end -->
