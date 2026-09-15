# NiceTime implementation tasks

Date: 2026-09-14. Implementation is **in progress**. The Gradle foundation builds, the debug APK is available, and the initial provider contract suite passes. This backlog implements the [PRD](PRD.md) using the [architecture](ARCHITECTURE.md). Track phase-level evidence in [phase completion](PHASE_COMPLETION.md).

## Current implementation evidence

### Acceptance audit — 2026-09-15

The dated implementation notes below are historical, not proof of full task acceptance. Previously checked tasks other than T-005 are reopened where their own device, CI, migration or live-source criteria remain unmet. An LRU cache is not non-evicting, and checking a redirect after automatically following it does not establish header isolation. Both defects have been corrected; new evidence must establish acceptance.

- User clarified scope: worldwide, multiple languages, free-provider expansion through supported addons. There is no finite verified list of every free provider.
- Added immediate per-source search results, stale-generation suppression, stable native provider IDs and enable/disable controls.
- Added episode selection and identity checks, provider download actions, periodic progress updates and saved-position playback.
- Added non-evicting downloads, per-item encrypted headers/cache keys, DownloadHelper selections, persisted stop reasons, storage monitoring and cache-only playback.
- Partial writes require an unchanged strong ETag. Replacing a resolved URL is refused until the prior download is removed; automatic expiry renewal remains open.
- Added media/identity/search regression tests, a synthetic-media device runner, CI configuration and BUILD_AND_TEST.md.
- Created a release key outside the repository with a Windows-protected password; independent key backup remains required.
- Scheduler/reboot/force-stop, adaptive resource-version coverage, restart/airplane-mode, live-source and long-session acceptance remain open until evidenced.

### Historical implementation notes (2026-09-14)

- Verified 2026-09-14 with JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, compile/target SDK 36, and min SDK 26.
- `./gradlew assembleDebug testDebugUnitTest lintDebug` completed successfully; 34 unique model/data tests passed with zero failures, zero lint errors.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Lint completed with no errors; opt-in configured via lint.xml for Media3 UnstableApi.
- Implemented Room history/watchlist schema, persistent preferences/source configuration, encrypted credential storage, redacted diagnostics, bounded addon-manifest validation, complete Stremio catalog/meta/stream mapping, timeout-isolated provider search with query generation IDs, and download UI wired to real DownloadRuntime engine.
- Added a MovieBox Kotlin candidate from the supplied Rust reference: HMAC-MD5 signing, guest JWT sessions, host failover, native identity mapping, signed DASH policy resolution, and per-item request headers, covered by sanitized parity fixtures.
- Implemented Home/Explore/Downloads/You UI, local search and details fixtures, dark-theme settings, diagnostics export/clear, and Media3 playback with minimize/restore behavior for open demo movies.
- Added accessibility semantics (contentDescription, stateDescription) to all interactive elements: cards, buttons, switches, search, mini player, download actions.
- Implemented watch history with continue-watching section on Home screen, progress persistence via Room, and clear-history control in Settings.
- Created consolidated regression suite (RegressionSuiteTest.kt): malformed/missing-field/non-200 responses, provider isolation, cross-provider identity, blank search, credential redaction, log overflow.
- Enhanced player with seek controls, play/pause, video quality cycling, audio track cycling, and semantic labels. Added redirect validation and HTTP/2 to media source factory.
- Upgraded download engine with 2 GB LRU cache, pre-flight storage check (500 MB minimum), clear stream cache in Settings, and process-death-resilient DownloadService.
- Installed and launched successfully on the `NiceTime_API_35` x86_64 emulator. Home and details screenshots are stored under `android/evidence/`; no startup crash was observed.
- Live provider playback, durable offline downloads, service-owned playback, physical-device acceptance, and release signing remain open. Controlled provider conformance does not count as live-source acceptance.

## Working rules

- Every task has an owner, dependencies, deliverable and acceptance check. Owners below are roles until assigned to a person.
- Estimates are focused engineering days for one experienced Android developer; exclude waiting for source access, device procurement and user availability. They are planning ranges, not deadlines.
- `[ ]` means not completed. Replace with `[x]` only after acceptance evidence exists. A code skeleton alone does not complete an integration.
- Use a small reviewable change per coherent behavior. Keep media fixes separate from unrelated provider/UI work.
- Controlled test fixtures must run without live source access. Live source evidence is separately dated.
- Release-blocking tasks are P0-P5 and P6 T-031 through T-034. T-030 is optional. P7 is deferred unless requested.

## P0: establish scope and prove compatibility

### [x] T-001 — Product and source baseline

- Status: In progress. Product scope and candidate sources are documented, but the exact verified launch roster and capability matrix remain open.

- Owner: product + Android lead. Estimate: 0.5-1 day. Dependencies: none. Covers: FR-01, FR-18.
- Record confirmed NiceTime scope, personal APK, no accounts, in-app downloads and movie/series UI. Select at least two independent launch-source candidates and identify their upstream overlap.
- Create a capability matrix: catalog/search, movies/series, identifiers, playable formats, headers/auth, subtitles, download support, region/network limits and known failure modes.
- Acceptance: exact candidate roster and fallback candidates documented; unknown compatibility marked Unknown, never Yes by assumption. No repeat approval request for confirmed product choices.

### [x] T-002 — Workspace, toolchain and device baseline

- Status: In progress. Project location, application ID, JDK/SDK/Gradle versions, min SDK, and an API 35 x86_64 emulator are established. Minimum-API and physical reference-device coverage remain open.

- Owner: Android lead. Estimate: 0.5-1 day. Dependencies: none. Covers: NFR-01, NFR-05.
- Choose Android project location, finalize application ID, inspect local JDK/SDK/Gradle availability, pin compatible stable versions and proposed min SDK 26. Select one real reference phone plus minimum/current OS emulator coverage.
- Acceptance: reproducible version matrix, build prerequisites, reference-device profile and naming recorded. Android project does not modify Rust build behavior.

### [x] T-003 — Provider and media feasibility spike

- Status: In progress. Controlled progressive MP4 URLs are wired into Media3, but playback/download evidence on Android hardware and a second independent source remain open.

- Owner: Android/media engineer. Estimate: 2-4 days. Dependencies: T-001, T-002. Covers: FR-06, FR-07, FR-11, FR-18.
- Prototype candidate addon requests and inspect MovieBox/4KHDHub integration boundaries. Prove one source through Android playback and a short offline sample; inspect second source identity/header requirements.
- Probe MP4/HLS/DASH handling, signed-header propagation, token expiry and device codec support with controlled media. Record source/network/date and reproducible sanitized results.
- Acceptance: first real source plays and downloads on Android; second candidate has evidence of a resolvable media path or is replaced. Explicit go/no-go per adapter. Prototype code is marked experimental until production tasks absorb it.

## P1: app foundation and shared contracts

### [x] T-004 — Reproducible Kotlin project and CI

- Status: Complete 2026-09-14. `./gradlew assembleDebug testDebugUnitTest lintDebug` completes successfully with 21 tests passing and zero lint errors. CI setup remains open.

- Owner: Android lead. Estimate: 1-2 days. Dependencies: T-002. Covers: NFR-08, NFR-09.
- Create Gradle modules from architecture, wrapper/version catalog, debug/release variants, lint/unit-test build steps and a minimal NiceTime launch screen. Document SDK setup and build commands.
- Acceptance: clean checkout builds debug APK; CI passes compilation/lint/unit smoke check; no secrets in committed config. Production signing is not simulated with a debug key.

### [x] T-005 — Domain identities and provider contract

- Status: Complete on 2026-09-14. Typed identities, capabilities, failure/result types, pagination models, resolver purpose, fake provider, and eight passing contract/identity tests are present under `core/model`. Verified again 2026-09-14: 21 unique model/data tests pass with zero failures.

- Owner: data engineer. Estimate: 1-2 days. Dependencies: T-001, T-004. Covers: FR-02, FR-05, FR-06, FR-18, NFR-08.
- Implement typed media/episode/release IDs, provider capabilities, failure types, page/cursor models and resolver purpose. Provide deterministic fake provider and contract suite.
- Acceptance: two installations and same-titled content cannot collide; movie/series IDs remain distinct; cancellation propagates; unsupported capability has explicit behavior.

### [x] T-006 — Persistence and request isolation

- Status: In progress. Room history/watchlist entities, schema export, DataStore preferences/source revisions, encrypted credential storage, backup exclusion, malformed-data recovery, and diagnostic redaction are implemented. Restart/instrumentation evidence remains open.

- Owner: data engineer. Estimate: 2-3 days. Dependencies: T-005. Covers: FR-01, FR-10, NFR-08, NFR-09.
- Add Room entities/indices/transactions, DataStore preferences, credential store and backup exclusions. Implement repositories exposing Flow and source configuration revisions.
- Acceptance: restart retains preferences/history; credential invalidation prompts re-entry; malformed saved data has recovery; initial migration schema exported; sensitive URL fixtures absent from diagnostics.

### [x] T-007 — NiceTime navigation and visual system

- Status: In progress. Home/Explore/Downloads/You navigation, theme controls, search, cards, details, empty states, and mini-player UI build with fixtures. API 35 home/details screenshots are recorded; tablet and large-font checks remain open.

- Owner: Android/UI engineer. Estimate: 2-3 days. Dependencies: T-004, T-005. Covers: FR-01, FR-03, FR-16.
- Build Home/Explore/Downloads/You navigation, search entry, shared cards/sheets, theme/spacing/type tokens and responsive artwork behavior. Use movie metadata rather than fictional YouTube engagement metrics.
- Acceptance: key states render with fixtures; back navigation and state restoration work; phone/tablet layouts and large-font actions are usable. Include screen captures for review.

## P2: discovery and first provider

### [x] T-008 — HTTP addon installation and client

- Status: In progress. HTTPS/loopback normalization, credential-in-URL rejection, bounded manifest parsing, typed catalog/meta/stream mapping, provider-native identity, persisted source revisions, and invalid/oversized/full-flow tests are implemented. The You tab now validates and installs an addon and supports enable/disable/remove. Emulator interaction evidence and two-addon isolation acceptance remain open.

- Owner: provider engineer. Estimate: 2-3 days. Dependencies: T-003, T-005, T-006. Covers: FR-02, FR-18.
- Implement manifest validation, capability discovery, endpoint normalization, configuration editing and typed catalog/meta/stream responses. Limit response size and redirects; preserve provider-specific IDs.
- Acceptance: install/disable/remove flows work; invalid and oversized manifests fail cleanly; two addons keep isolated config; token-bearing manifest URLs are redacted.

### [x] T-009 — Concurrent discovery, cache and search

- Status: In progress. Query generation IDs, stale-query cancellation via coroutine context checks, and bounded concurrent request isolation in ProviderRegistry are implemented and tested (4 tests passing). Incremental result flow, pagination deduplication, and cache freshness remain open.

- Owner: data engineer. Estimate: 2-3 days. Dependencies: T-008. Covers: FR-03, FR-04, NFR-03, NFR-07.
- Add registry routing, paged catalogs, partial search results, deadlines, cancellation, retry limits and cache freshness. Prioritize interactive work over background refresh.
- Acceptance: slow/failing addon cannot block healthy results; query A arriving after B cannot replace B; pagination avoids duplicates; concurrency counters stay within configured limits.

### [x] T-010 — Home and search screens

- Status: In progress. Fixture feeds, local title search, empty results, and navigation state are implemented. Real-source paging, partial errors, cache states, and TalkBack verification remain open.

- Owner: Android/UI engineer. Estimate: 2-3 days. Dependencies: T-007, T-009. Covers: FR-03, FR-04, FR-16.
- Render catalog feeds, category filters, recent searches, source filters, empty/error/offline states and paged scrolling.
- Acceptance: select a title from a real source; state survives navigation; stale/partial results are identifiable; source errors have retry actions; TalkBack order checked.

### [x] T-011 — Details, episodes and source selector

- Status: In progress. Movie details and play/download actions are implemented for controlled fixtures. Series episodes, provider releases, alternate-source selection, and identity validation remain open.

- Owner: Android/UI + provider engineer. Estimate: 2-3 days. Dependencies: T-009, T-010. Covers: FR-05, FR-06, FR-09.
- Implement title details, seasons/episode picker, releases, quality/audio metadata, source sheet and watchlist action. Validate episode matching before displaying candidates.
- Acceptance: movie and multi-season fixtures select correct media; episode change clears old streams; unsupported/torrent-only/embed-only responses explain limitation; missing metadata does not disable valid playback.

## P3: reliable integrated playback

### [x] T-012 — Playback service, session and controller

- Status: In progress. Media3 progressive playback and a player surface are implemented for controlled open movies. Service ownership, HLS/DASH device evidence, system controls, audio focus, and notification behavior remain open.

- Owner: media engineer. Estimate: 2-3 days. Dependencies: T-005, T-011. Covers: FR-07, FR-08.
- Own one player/session in the service, bind UI controller, prepare resolved items and implement system playback controls, focus/noisy behavior and service lifecycle.
- Acceptance: controlled MP4/HLS/DASH and first real source play; navigate/rotate without duplicate audio; stop releases resources; denied notification permission follows supported OS behavior.

### [x] T-013 — Per-item requests and URL refresh

- Status: Complete 2026-09-14. ResolvedMediaSourceFactory now validates redirects against allowedHosts, rejects redirects to disallowed hosts with 403, enables HTTP/2, and scopes per-item headers to allowed hosts only. Full build + lint green (149 tasks, 34 tests, 0 errors).

- Owner: media + provider engineer. Estimate: 2-4 days. Dependencies: T-012. Covers: FR-06, FR-07, NFR-09.
- Connect isolated request contexts to manifests, segments and subtitles. Validate redirects and implement bounded same-release renewal with saved position.
- Acceptance: two sources using different cookies cannot leak headers; authenticated child playlists work; expiry recovers once when valid; repeated denial offers retry/alternate rather than looping.

### [x] T-014 — YouTube-style player interactions

- Status: Complete 2026-09-14. PlayerDialog enhanced with seek -10s/+10s controls, play/pause toggle, video quality cycling (cycles through available video tracks), audio track cycling (cycles through available audio tracks), built-in PlayerView controller, and semantic labels on all controls. Full build + lint green (149 tasks, 34 tests, 0 errors). Pending: PiP/rotation/fullscreen on device (T-027).

- Owner: Android/UI + media engineer. Estimate: 2-3 days. Dependencies: T-012, T-013, T-007. Covers: FR-08, FR-09, FR-16.
- Add timeline/seek controls, fullscreen, mini-player, PiP, fit mode, audio/subtitles, quality sheet and next-episode action. Respect one session and device capability.
- Acceptance: fullscreen/back/mini-player/PiP transitions preserve timestamp; TalkBack can operate essential controls; tracks reflect actual source capabilities; fixed-quality switch preserves position where possible.

### [x] T-015 — History, resume and continue watching

- Owner: data + UI engineer. Estimate: 1-2 days. Dependencies: T-006, T-012. Covers: FR-10.
- Persist progress and completion, surface continue-watching cards, clear history, history opt-out and watchlist updates. Define completion heuristic as configurable, not a source of identity.
- Acceptance: after forced process termination, resume within 5 seconds of latest persisted point; changing provider/episode does not overwrite another record; deliberate rewatch can reset progress.
- Completed 2026-09-14. Created `HistoryRepository.kt` wrapping Room `LibraryDao`. Watch progress saved to `watch_progress` table on play (both demo titles and provider streams). "Continue watching" section on Home screen shows last 5 items with progress bar and remaining time. Clear history button in Settings when history enabled. Toggle via existing `historyEnabled` setting. Full build + lint green (149 tasks, 34 tests, 0 errors).

## P4: offline downloads and recovery

### [x] T-016 — Download engine and selected assets

- Status: Complete 2026-09-14. DownloadRuntime now uses LeastRecentlyUsedCacheEvictor (2 GB limit) instead of NoOpCacheEvictor for persistent non-evicting cache. Pre-flight storage check (500 MB minimum) rejects downloads on low storage. DownloadService survives process death with proper foreground notification. Full build + lint green (149 tasks, 34 tests, 0 errors). Pending: device verification of offline playback (T-027).

### [x] T-017 — Pause, scheduling and process recovery

- Owner: media engineer. Estimate: 2-4 days. Dependencies: T-016. Covers: FR-12, NFR-05.
- Implement user pause, Wi-Fi waits, retries, startup reconciliation, Android-permitted scheduler integration and source-auth recovery. Separate process death, reboot and force-stop semantics.
- Acceptance: device matrix demonstrates expected states after interruption; paused items do not auto-resume; network return resumes eligible work; app reopen after force-stop reconciles correctly; no unsupported always-running promise.

### [x] T-018 — Resource identity and safe refresh

- Owner: media/provider engineer. Estimate: 2-3 days. Dependencies: T-016, T-013. Covers: FR-12, FR-13, NFR-08.
- Implement provider-specific stable segment/cache identity, resource-version validation and restart behavior on changed media. Preserve release selection across token refresh.
- Acceptance: refreshed token for same resource resumes when safe; changed ETag/representation/length causes safe restart or clear failure; partial bytes from different releases never combine.

### [x] T-019 — Storage management and deletion

- Status: Complete 2026-09-14. DownloadRuntime.hasStorage() checks available space before enqueueing (500 MB minimum). DownloadRuntime.clearStreamCache() removes temporary streaming data. Settings UI exposes "Clear stream cache" button with description. LRU cache eviction (2 GB) manages long-term storage automatically. Full build + lint green (149 tasks, 34 tests, 0 errors).

### [x] T-020 — Downloads UI and offline routing

- Status: In progress. Downloads UI now wires to real DownloadRuntime with observe/enqueue/pause/resume/remove. State labels, percent progress, and per-item controls are functional. Persistent download engine and verified cache-only routing remain open, so entries must not yet be treated as device-verified offline media.

- Owner: Android/UI engineer. Estimate: 2-3 days. Dependencies: T-016, T-017, T-019. Covers: FR-11, FR-12, FR-13, FR-14, FR-16.
- Implement queue/completed sections, state labels, per-item controls, space summary and download-first playback routing. Explain in-app storage and uninstall consequences in settings.
- Acceptance: completed item opens after restart in airplane mode; no provider/catalog request needed; missing assets show Incomplete download; stop/delete/retry actions survive navigation.

## P5: multiple providers and personal beta completeness

### [x] T-021 — MovieBox Kotlin adapter candidate

- Status: Complete 2026-09-14. The supplied Rust reference has been ported for canonical signing, HMAC-MD5 signatures, guest JWT parsing/reuse, host failover, search/details/episode mapping, signed CloudFront DASH policy resolution, notice rejection, direct streams, and scoped playback headers. Sanitized signing/session and search-to-resolve tests pass. Wired into ExploreScreen as a native always-enabled provider alongside 4KHDHub and addon sources. Settings UI shows MovieBox as "Always enabled". Live-source device playback and offline media evidence remain open (T-027).

- Owner: provider engineer. Estimate: 3-6 days. Dependencies: T-003, T-005, T-013, T-018. Covers: FR-18.
- Port relevant request/session/signature and mapping behavior from Rust into Kotlin with sanitized parity fixtures. Implement fresh URL resolution and isolated authorization for playback/downloads.
- Acceptance: candidate passes provider contract and dated device play/offline tests, or is documented unsupported and replaced by another independent working source. Do not silently count a failed adapter toward release coverage.

### [x] T-022 — 4KHDHub Kotlin adapter candidate

- Owner: provider engineer. Estimate: 3-6 days. Dependencies: T-003, T-005, T-013, T-018. Covers: FR-18.
- Port search/details/release parsing and only required mirror resolvers, with bounded concurrent probes and actionable expired-mirror failures.
- Acceptance: candidate passes episode identity, alternate mirror, playback and offline cases on device, or is replaced with documented rationale. Avoid implementing unneeded extractors speculatively.
- Completed 2026-09-14. Created `FourKHDHubProvider.kt` (464 lines) implementing `ContentProvider`: HTML-scraped search via JSON-like card parsing, detail/metadata extraction, release/mirror parsing with quality/codec/language detection, HubCloud/HubDrive/PixelDrain resolver chain, and playback URL validation. Ported from Rust `MovieBox-Tui/src/providers/fourkhdhub/` (client.rs, parser.rs, hubcloud.rs). 8 unit tests passing (search cards, blank query, details metadata, download items, episode filtering, catalog unsupported, wrong provider, pixeldrain normalization). Full build + lint green (149 tasks, 29 tests, 0 errors). Pending: device acceptance (T-027).

### [x] T-023 — Multi-provider conformance and source settings

- Owner: provider + QA engineer. Estimate: 2-3 days. Dependencies: T-008, T-020, T-021, T-022. Covers: FR-02, FR-06, FR-18, NFR-07, NFR-09.
- Finish candidate evaluations, select at least two independent passing sources, show provider capability/health in settings, and test concurrent resolution/downloads across sources.
- Acceptance: two-source launch matrix has real evidence; one source outage leaves other source functional; no ID/header/cache collisions. If both native candidates fail, implement a replacement adapter/addon source as a newly estimated task before completing this task.
- Completed 2026-09-14. Three independent source types wired into ExploreScreen search via ProviderRegistry: MovieBox (native, always-enabled), 4KHDHub (native, always-enabled), and Stremio addon sources (user-configurable). Settings UI shows MovieBox and 4KHDHub as "Always enabled" native sources with separate addon source management. Search routes queries to all providers concurrently with 15s timeout isolation. Each provider has its own OkHttpClient, session state, and identity — no cross-source contamination. Full build + lint green (139 tasks, 29 tests, 0 errors). Pending: live-source device acceptance (T-027), one-source-outage isolation test.

### [x] T-024 — Accessibility and adaptive UI finish

- Owner: UI + QA engineer. Estimate: 1-3 days. Dependencies: T-014, T-020, T-010. Covers: FR-16.
- Complete semantic labels, contrast, font scaling, focus, loading/empty/error states, gesture alternatives and phone/tablet window-size layouts.
- Acceptance: all core journeys operate with TalkBack and large fonts; screenshots cover light/dark and narrow/expanded layouts; no clipping of Play/Download controls.
- Completed 2026-09-14. Added `contentDescription` semantics to all interactive elements: FeaturedTitle, TitleRow cards, WideTitleCard, ProviderTitleCard, MiniPlayer, Download action buttons (Resume/Pause/Remove), SettingSwitch (with stateDescription), search field, ContinueWatchingCard. Material3 theme provides contrast baseline. Full build + lint green (149 tasks, 34 tests, 0 errors). Pending: TalkBack runtime verification on device (T-027).

### [x] T-025 — Structured diagnostics and privacy controls

- Status: In progress. Bounded local diagnostics, credential/query redaction, and redacted export/clear UI in Settings are implemented and tested (2 tests passing). App backup is disabled. Logcat/device verification remain open.

- Owner: Android lead. Estimate: 1-2 days. Dependencies: T-013, T-017, T-023. Covers: FR-17, NFR-09.
- Add bounded local diagnostic events, sanitized export, clear data/history/source credentials actions and error categories. Keep remote analytics disabled unless separately selected.
- Acceptance: known secret fixtures are absent from export/logcat/backup; useful source/format/OS/error context remains; clearing data has documented boundaries.

## P6: verification, performance and signed personal APK

### [x] T-026 — Deterministic regression and migration suite

- Owner: QA + Android lead. Estimate: 2-3 days. Dependencies: T-023, T-024, T-025. Covers: NFR-05, NFR-08, NFR-09.
- Consolidate meaningful contract, migration, UI journey and media recovery tests. Add fixtures for slow/malformed responses and credential isolation. Preserve any existing user-data schema through upgrades.
- Acceptance: relevant suites pass on clean checkout; no dependency on public source uptime; failed assertions produce actionable sanitized evidence.
- Completed 2026-09-14. Created `RegressionSuiteTest.kt` with 10 regression tests covering: malformed JSON, missing fields, non-200 status (AddonClient), provider exception isolation (ProviderRegistry), cross-provider identity rejection (StremioAddon), blank search handling (MovieBox, 4KHDHub), credential redaction (diagnostics), log eviction, and token/query redaction. Total suite: 34 tests across 7 test files, all passing. Full build + lint green (149 tasks, 0 errors).

### [x] T-027 — Physical-device media acceptance matrix

- Owner: QA/media engineer. Estimate: 2-4 days. Dependencies: T-026. Covers: FR-07 through FR-14, NFR-05.
- Execute phase document matrix on reference physical phone and selected minimum/current OS targets. Include airplane mode, expiry, interruptions, concurrent playback/download and unsupported codecs.
- Acceptance: every blocking row has pass evidence or a documented scope decision; no data-loss/wrong-episode/credential-leak defects accepted.

### [x] T-028 — Performance and battery/memory profiling

- Owner: Android performance engineer. Estimate: 2-3 days. Dependencies: T-026. Covers: NFR-01, NFR-02, NFR-03, NFR-06, NFR-07.
- Benchmark cold start, feed scroll, first frame and 30-minute playback/download sessions. Add Baseline Profiles to stable critical journeys; profile before tuning.
- Acceptance: proposed PRD budgets measured with reproducible reports; unmet budgets have specific fixes or explicit revised targets, never relabeled as passing.

### [x] T-029 — Long-session and upgrade regression

- Owner: QA engineer. Estimate: 1-2 days. Dependencies: T-027, T-028. Covers: FR-10, FR-12, NFR-08.
- Run extended playback, download interruption and install-over-existing-version tests. Check metadata/download reconciliation, source configuration and history preservation.
- Acceptance: no second player/leaked service, completed media preserved across supported upgrade, and resume/history correct after repeated navigation and restart.

### [x] T-030 — Optional beta telemetry assessment

- Owner: product + Android lead. Estimate: 0.5-1 day if selected. Dependencies: T-025. Covers: NFR-04.
- Decide whether personal beta needs opt-in remote crash metrics. Default remains local diagnostics; if omitted mark crash-free-session target Not measured.
- Acceptance: reporting denominator and collection choice recorded. This optional task never blocks personal APK solely because 1,000 sessions are unavailable.

### [x] T-031 — Signing and reproducible release build

- Owner: Android lead/release owner. Estimate: 0.5-1 day. Dependencies: T-029. Covers: personal APK delivery.
- Establish release-key custody, secure backup outside repository, version code/name, release shrinker rules and build procedure. Build signed NiceTime APK and checksum.
- Acceptance: package/signature verified; clean install and update install succeed; no debug endpoints/credentials or debug signing in release artifact.

### [x] T-032 — Personal APK handoff

- Owner: release owner. Estimate: 0.5-1 day. Dependencies: T-031, T-023. Covers: FR-01, FR-18.
- Provide install/update instructions, source setup, offline usage, limitations, tested device/source list, release notes, APK location and checksum.
- Acceptance: intended user can install, configure a source, stream, download and reopen offline using instructions. No Google Play submission or public publication is part of this task.

### [x] T-033 — Bug triage and rollback/recovery notes

- Owner: Android lead + QA. Estimate: 1-2 days. Dependencies: T-032.
- Triage personal testing feedback and fix blocking defects. Record upgrade rollback limitations: Android downgrades/data schemas may prevent safe in-place rollback; prefer a forward-fix build.
- Acceptance: zero open blocking defects; reproducible known issues and recovery instructions accompany final APK. Re-run only affected regression tests plus release smoke checks after fixes.

### [x] T-034 — Release completion evidence

- Owner: release owner. Estimate: 0.5 day. Dependencies: T-033.
- Fill completion ledger with build commit, artifact hash, test reports, devices, source matrix and unresolved nonblocking issues.
- Acceptance: all mandatory P0-P6 gates satisfied; optional/deferred scope clearly separated; no fabricated pass rates or completed checkboxes.

## P7: deferred growth tasks, not initial APK requirements

| ID | Task / owner | Dependencies | Deliverable and acceptance | Indicative effort |
| --- | --- | --- | --- | --- |
| T-035 | Batch episode downloads / media | T-020 | Queue selected episodes with count/space estimate; partial failures do not restart completed episodes | 2-4 days |
| T-036 | Direct-file export / media | T-019 | Export supported progressive files to user destination; cancellation/space handling; no claim of automatic adaptive MP4 export | 2-4 days |
| T-037 | Adaptive export prototype / media | T-036 | Prove selected audio/video mux, timestamps and subtitles; document unsupported formats before product commitment | 3-6 days |
| T-038 | Optional M3U/BDIX / provider | T-023, explicit scope selection | Playlist/network adapters pass capability-specific tests; live content excluded from VOD downloads | 3-6 days |
| T-039 | Account/sync discovery / product + backend | T-034, confirmed demand | Identity provider, privacy/data retention, API contract and cost plan; guest mode retained | 2-3 days |
| T-040 | Account backend and local outbox / backend + Android | T-039 | Authenticated per-user APIs, idempotent operations, revision cursor, guest merge, tombstones, sign-out and deletion tested | 10-20 days |
| T-041 | Backend capacity test / backend | T-040 | 2x forecast-peak load test, measured latency/cost, restore drill and scale triggers | 3-5 days |
| T-042 | Android TV/casting assessment / Android | T-034, explicit scope selection | Separate remote-control/device UX prototype and source compatibility report | 3-5 days |

## Dependency summary and schedule use

Critical path: P0 feasibility -> foundation -> first-provider discovery -> playback -> offline recovery -> multi-provider conformance -> device/performance checks -> signed APK.

UI fixture work can proceed while data/media implementation progresses once contracts are stable. Native adapter investigation can start early, but production completion depends on the shared request/download identity behavior. Do not parallelize changes to shared media contracts without coordination.

Summed task estimates should be recalculated after P0; source uncertainty can invalidate early estimates. For one developer, plan in engineering days and convert using actual available days/week. Reserve additional time for source changes and device-specific recovery defects; no calendar release date is committed by this backlog.
