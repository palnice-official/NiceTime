# NiceTime Android planning pack

Date: 2026-09-14. App name: NiceTime (confirmed).

Status: implementation in progress. See the 2026-09-15 audit in TASKS.md: older completion counts overstated acceptance. This pass adds media/request isolation, persistent per-item download controls, cache-only routing, episode selection, incremental search and native provider controls. Device testing and signed-build verification are in progress.

User clarification (2026-09-15): prioritize worldwide free sources in multiple languages. Keep addon installation extensible; do not represent every worldwide source as verified. A new personal signing key has been created outside the repository because no previous key existed. An independent secure backup is still required.

Build and test instructions: [BUILD_AND_TEST.md](BUILD_AND_TEST.md). Candidate capability record: [SOURCE_MATRIX.md](SOURCE_MATRIX.md).

## Documents

1. [PRD](PRD.md): audience, scope, screens, requirements, acceptance criteria, and success measures.
2. [Architecture](ARCHITECTURE.md): Kotlin structure, provider contracts, playback, downloads, persistence, and growth strategy.
3. [Tasks](TASKS.md): dependency-ordered implementation backlog with deliverables and verification.
4. [Phase completion](PHASE_COMPLETION.md): milestone gates, evidence requirements, and honest progress tracking.

Requirement IDs `FR-*` and `NFR-*` link the PRD to tasks. Task IDs `T-*` link tasks to phase gates. `P0` means the discovery phase; Must/Should/Later indicate feature priority.

## Decision register

| ID | Decision | Working assumption | Status / consequence |
| --- | --- | --- | --- |
| D01 | Meaning of YouTube-like | Familiar feed, search, watch page, mini-player, library; movies and series only | Confirmed by user; creator uploads and social features excluded |
| D02 | First sources | Multiple providers; HTTP addons, MovieBox and 4KHDHub are candidates; M3U optional | Multi-provider requirement confirmed; exact launch roster finalized by compatibility evidence |
| D03 | Identity | Guest-first; optional account sync later | Confirmed; MVP works without a backend |
| D04 | Downloads | In-app offline viewing; file export only if requested later | Confirmed; adaptive streams do not automatically become MP4 files |
| D05 | Distribution | Personal APK initially | Confirmed; public/store distribution is outside current release |
| D06 | Platforms | Android phones, adaptive tablet layouts; Android TV later | Proposed; minimum SDK 26 subject to dependency/device validation |
| D07 | Name and visual identity | NiceTime; original YouTube-inspired interaction design | Name confirmed; propose `app.nicetime.android`, finalize before first signed APK |
| D08 | Languages | English UI first, localization-ready; media language selectable | Proposed; ask for launch languages during discovery |
| D09 | Team, timeline, budget | One Android developer with part-time design/QA | Unconfirmed; estimates are ranges, not commitments |
| D10 | Monetization and content offering | No billing or ads in initial beta | Unconfirmed; public catalog and content permissions require a product decision |

## How to use and update this pack

- Preserve confirmed answers above; resolve remaining engineering choices during P0.
- Propagate each changed decision into all four documents; record the affected requirement and task IDs.
- Finish P0 before treating the exact source roster and SDK/toolchain as an implementation baseline. Documentation alone does not establish provider playback compatibility.
- Implement a small end-to-end stream/download slice before broad UI or provider expansion.
- Add dated evidence to the completion ledger as work finishes; do not mark tests passed before running them.
- Keep the existing Rust TUI operational. The proposed Android project lives under a new `android-app/` directory or a separate repository, chosen in P0.

## Repository context

NiceTime is now an Android Gradle project at the workspace root. The supplied Rust provider implementations in `MovieBox-Tui/src/providers` are behavioral references for Kotlin ports; they are not compiled into the APK. Live provider acceptance remains separate from controlled fixture conformance.
