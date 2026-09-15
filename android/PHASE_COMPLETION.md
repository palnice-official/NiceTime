# NiceTime phase completion and release evidence

Date: 2026-09-14. This file is a living tracker. Android implementation is **in progress**; only evidence-backed task completion is counted.

References: [PRD](PRD.md), [architecture](ARCHITECTURE.md), [task backlog](TASKS.md), [confirmed decisions](README.md#decision-register).

## 1. Status rules

Allowed task statuses: Not started, In progress, Blocked, In review, Complete, Deferred. Blocked needs a concrete dependency and an action to unblock. Deferred needs a reason and must not hide a mandatory release requirement.

A phase is Complete only when all mandatory deliverables and acceptance checks have evidence. Passing a demo does not establish offline recovery, device compatibility, or production provider availability. Completion evidence includes commit/build, date, device/OS, test procedure, observed result and artifact/report location.

Planning completion is tracked separately from implementation. No phase may become Complete merely because its Markdown section exists or an estimate has elapsed.

## 2. Current dashboard

Acceptance audit: 2026-09-15. The previous "13 of 33" count included tasks whose own acceptance criteria had not run. That count is withdrawn. T-005 retains its contract-test completion; other implementation claims require the outstanding evidence listed in TASKS.md.

| Phase | Current status | Remaining acceptance |
| --- | --- | --- |
| P0 | In progress | Exact verified independent-source roster and minimum-API coverage |
| P1 | In progress | Hosted CI, restart/migration checks, adaptive UI evidence |
| P2 | In progress | Paging/cache freshness, real-source journeys and episode cases |
| P3 | In progress | Full lifecycle, expiry recovery, PiP and history recovery |
| P4 | In progress | Restart/reboot, adaptive assets, resource versions and offline matrix |
| P5 | In progress | Two independent live sources, multilingual tracks and accessibility |
| P6 | In progress | Sustained profiling, upgrades, signed APK verification and key backup |
| P7 | Deferred | Outside the current personal-APK scope |

Code delivered in this pass includes scoped HTTP redirects, non-evicting offline storage, cache-only playback, per-item pause, encrypted request contexts, strong-ETag append protection, episode selection, partial search and provider controls. The dated evidence report distinguishes verified behavior from remaining work. Worldwide, multiple-language provider expansion is confirmed; untested addons are not counted as supported sources.

## 3. Common definition of done

- [ ] Behavior satisfies linked PRD requirements and handles empty/loading/failure states where applicable.
- [ ] Implementation follows module boundaries and uses stable provider/media identities.
- [ ] Relevant deterministic tests pass; tests exercise failure/recovery behavior rather than merely repeating implementation.
- [ ] Applicable physical-device or emulator checks are recorded.
- [ ] No unrelated changes, embedded credentials, raw signed URLs in logs, or new blocking regressions.
- [ ] UI changes include screenshots or a short recording; accessibility checked where affected.
- [ ] Persistence changes include migration/reconciliation evidence.
- [ ] Documentation and completion ledger reflect actual behavior and limitations.
- [ ] Named reviewer or implementer records completion date and evidence. A separate reviewer is desirable if available, not an invented staffing prerequisite.

## 4. P0 gate — scope and feasibility

Entry: confirmed NiceTime product preferences and source repository available.

- [ ] T-001: Confirmed product decisions preserved; proposed candidate roster includes two independent sources.
- [ ] T-002: Application ID, repository location, dependency versions, SDK/JDK and reference devices recorded.
- [ ] T-003: One real source plays and downloads a short sample on Android; another has a valid candidate media path or replacement.
- [ ] Provider matrix distinguishes proven support, unsupported and unknown features.
- [ ] Source-specific constraints and unresolved risks have owners.

Demo: open a source title on Android, play it, download a short sample and reopen offline.

Exit artifacts: decision note, version/device matrix, sanitized request/media evidence and candidate compatibility report.

Gate failure: unresolved media access, unsupported required formats, or no plausible second source. Continue fixture/UI work if useful, but do not call source feasibility complete.

## 5. P1 gate — application foundation

Entry: toolchain established; shared contracts based on P0 findings.

- [ ] T-004: Clean checkout builds and launches a debug NiceTime APK.
- [ ] T-005: Provider identity and cancellation contract tests pass.
- [ ] T-006: Settings and library data survive restart; secrets are isolated; schema exported.
- [ ] T-007: Home/Explore/Downloads/You navigation and responsive fixture screens render.
- [ ] Debug/release application IDs do not collide; no release signing key is committed.

Demo: navigate all main screens with fixture data, change a setting and verify persistence after restart.

Exit artifacts: build logs, debug APK, initial screenshots, schema and contract-test report.

## 6. P2 gate — discovery and first source

Entry: foundation passes; candidate addon endpoint is available for dated smoke tests.

- [ ] T-008: Addon install/configure/disable/remove and invalid-input handling work.
- [ ] T-009: Partial results, pagination, timeouts and stale-query cancellation verified.
- [ ] T-010: Home/search show actual source content with usable offline/empty/error states.
- [ ] T-011: Details, seasons, episodes and releases preserve identity.
- [ ] Missing/unsupported streams cannot appear as valid playable releases.

Demo: search a movie and a series; select the correct episode; simulate one failing addon while a healthy source still responds.

Exit artifacts: discovery recording, provider fixture tests, concurrency/deadline report and source capability record.

## 7. P3 gate — playback

Entry: reliable title/episode/release resolution.

- [ ] T-012: Service-owned single player handles controlled MP4/HLS/DASH and first real provider.
- [ ] T-013: Per-host headers and bounded URL refresh pass isolated request tests.
- [ ] T-014: Fullscreen, mini-player, rotation, PiP and tracks work without duplicate playback.
- [ ] T-015: History/continue watching, clear/opt-out and restart resume pass.
- [ ] Focus loss, headset removal, app background behavior and explicit stop are verified.
- [ ] Unsupported codecs and source denial produce useful actions without infinite retry.

Demo: play an episode, browse in mini-player, expand, select subtitles, interrupt and resume at saved position.

Exit artifacts: playback test matrix, transition recording, secret-isolation and first-frame measurements.

## 8. P4 gate — downloads and offline use

Entry: player and request-context handling pass P3.

- [ ] T-016: Persistent queue, selected tracks, stable IDs and offline cache work.
- [ ] T-017: Pause/network/restart/reboot/force-stop cases have correct observed behavior.
- [ ] T-018: Safe URL renewal preserves content identity; changed resources cannot corrupt partial files.
- [ ] T-019: Low space, deletion and independent cache cleanup are verified.
- [ ] T-020: Downloads screen accurately reflects state and opens completed media without network.
- [ ] Selected subtitles and metadata remain available offline.
- [ ] App does not label an adaptive offline cache as an exported MP4 file.

Demo: queue media, pause, restart app, resume, complete, enter airplane mode, restart again, play and seek, then delete and measure freed space.

Exit artifacts: download index/reconciliation tests, airplane-mode recording, storage report and interruption matrix.

## 9. P5 gate — multi-provider personal beta

Entry: first source works end to end, including offline playback.

- [ ] T-021 and T-022: Candidate native adapters evaluated with dated evidence; only passing adapters enabled as supported.
- [ ] T-023: At least two independent real sources pass documented capabilities; controlled fixtures and duplicate upstream configs do not count.
- [ ] T-023: Cross-source concurrency has no cookie, identity, subtitle or cache contamination.
- [ ] T-024: Core screens pass TalkBack, large-font, light/dark and responsive checks.
- [ ] T-025: Diagnostics are useful and redacted; history/source data controls work.
- [ ] Product requirements FR-01–FR-14 and FR-16–FR-18 have an implementation path and verification record.

Demo: search/play/download across two sources; disable or fail one and complete the journey with the other.

Exit artifacts: final source roster, capabilities/limitations, source outage report, UX recording and accessibility results.

Gate failure: fewer than two working independent sources. Add a scoped replacement-source task and revise estimate; do not silently reduce the user's multi-provider requirement.

## 10. P6 gate — signed personal APK

Entry: feature-complete multi-provider beta.

- [ ] T-026: Deterministic regression/migration suite passes.
- [ ] T-027: Required device and media acceptance rows pass.
- [ ] T-028: Performance budgets measured and resolved; evidence includes network and device context.
- [ ] T-029: Long-session and install-upgrade checks pass.
- [ ] T-031: Signed release APK, version, checksum and release-key custody documented.
- [ ] T-032: Install/update, source setup and offline-use instructions tested.
- [ ] T-033: Blocking feedback fixed and relevant regression checks rerun.
- [ ] T-034: Completion ledger and known limitations match the final build.
- [ ] T-030, if selected: opt-in telemetry outcome recorded; otherwise crash-free session target remains Not measured.

Demo: install the actual signed APK on the intended phone, configure sources, stream a movie and episode, download one, restart in airplane mode and play it.

Release condition: zero known blocking issues involving crash on core journey, wrong episode, lost/corrupted completed media, credential leakage, unusable offline playback or failed install/upgrade. Cosmetic issues can be recorded with severity and a follow-up task.

Exit artifacts: final APK/checksum, tested device/source matrix, test reports, release notes and known issues. No Play Store submission is required.

## 11. Mandatory acceptance scenario matrix

Run the applicable cases on the chosen minimum-supported API target, current target-OS emulator and a physical midrange phone. Media performance and hardware codec claims require physical-device evidence. Include at least one restricted-battery/OEM device if available; record gaps explicitly.

| Case | Procedure | Required result | Task | Status |
| --- | --- | --- | --- | --- |
| AT-01 | Fresh install, no account or sources | Useful setup/empty state; no forced login | T-007, T-008 | Not run |
| AT-02 | Rapid search A -> B with delayed A response | Only B owns active results | T-009 | Not run |
| AT-03 | Fail one of two sources | Healthy source remains usable; error attributed | T-023 | Not run |
| AT-04 | Same title on two sources, multiple seasons | Correct provider and episode retained | T-005, T-011 | Not run |
| AT-05 | Controlled MP4/HLS/DASH stream and seek | Correct first frame and seek; codec limits reported | T-012 | Not run |
| AT-06 | Manifest/segments/subtitles need scoped headers | Requests succeed only with intended scope; no cross-host leak | T-013 | Not run |
| AT-07 | Token expires during playback/download | Bounded refresh or actionable failure; no loop | T-013, T-018 | Not run |
| AT-08 | Rotate, mini-player, fullscreen, PiP | One session and preserved position | T-014 | Not run |
| AT-09 | Focus loss/headset removal/background toggle | Playback follows explicit policy | T-012, T-014 | Not run |
| AT-10 | Kill process after progress persisted | Resume within 5 seconds of persisted position | T-015 | Not run |
| AT-11 | Download same selection twice | One active logical download | T-016 | Not run |
| AT-12 | Select audio/subtitles then finish download | Only chosen supported assets persist and play offline | T-016, T-020 | Not run |
| AT-13 | Pause, background, restart | User pause stays paused; eligible queue persists | T-017 | Not run |
| AT-14 | Wi-Fi lost with Wi-Fi-only enabled | Wait state; no unintended cellular transfer | T-017 | Not run |
| AT-15 | Reboot and force-stop then reopen | Index/cache reconcile; no false uninterrupted-service claim | T-017 | Not run |
| AT-16 | Refresh URL changes underlying media | Safe restart/failure; no mixed-resource bytes | T-018 | Not run |
| AT-17 | Unknown size fills disk toward reserve | Controlled stop and recoverable state | T-019 | Not run |
| AT-18 | Completed download, airplane mode, restart, seek | Cache-only playback; no online resolver dependency | T-020 | Not run |
| AT-19 | Remove one required local asset | Incomplete-download error; no silent online fallback | T-020 | Not run |
| AT-20 | Delete while interrupted; reopen | Idempotent cleanup; other downloads intact | T-019 | Not run |
| AT-21 | Clear streaming cache | Completed downloads still playable | T-019 | Not run |
| AT-22 | Two providers transfer with different tokens | No request/cache contamination | T-023 | Not run |
| AT-23 | TalkBack and 200% fonts, narrow/tablet layout | Core actions reachable and labeled | T-024 | Not run |
| AT-24 | Export diagnostics with secret fixtures | No raw credentials or signed URLs | T-025 | Not run |
| AT-25 | Install new signed version over previous | Settings/history/download metadata preserved | T-029, T-031 | Not run |
| AT-26 | Thirty-minute playback/download session | No unbounded memory growth or duplicate player | T-028, T-029 | Not run |

## 12. Requirement traceability

| PRD requirements | Implementation tasks | Completion phase |
| --- | --- | --- |
| FR-01–FR-02 | T-001, T-006–T-008, T-023 | P1–P2, P5 |
| FR-03–FR-04 | T-007, T-009, T-010 | P2 |
| FR-05–FR-06 | T-005, T-011, T-013 | P2–P3 |
| FR-07–FR-09 | T-012–T-014, T-027 | P3, verified P6 |
| FR-10 | T-006, T-015, T-029 | P3, verified P6 |
| FR-11–FR-14 | T-016–T-020, T-027 | P4, verified P6 |
| FR-15 | T-038 | P7 optional |
| FR-16 | T-007, T-010, T-014, T-020, T-024 | P5 |
| FR-17 | T-025 | P5 |
| FR-18 | T-001, T-003, T-008, T-021–T-023 | P5 |
| FR-19 | T-035–T-037 | P7 optional |
| FR-20 | T-039–T-041 | P7 optional |
| NFR-01–NFR-03, NFR-06–NFR-07 | T-009, T-028 | P6 |
| NFR-04 | T-030 if selected | P6 optional measurement; insufficient data remains Not measured |
| NFR-05, NFR-08, NFR-09 | T-006, T-013, T-017–T-019, T-023, T-025–T-027, T-029 | P6 |

## 13. Risks and blocker register

These are planning uncertainties, not current execution failures.

| Risk | Impact | Owner | Mitigation / exit evidence |
| --- | --- | --- | --- |
| Launch source changes or unavailable | Source integration delay | Provider engineer | P0 spikes, capability fixtures, replacement source task, dated smoke test |
| URL/header renewal changes content identity | Corrupt or unusable downloads | Media engineer | T-018 version/segment tests; restart when identity uncertain |
| Android background limits or OEM restriction | Downloads wait unexpectedly | Media engineer | Checkpoint/reconcile; device-specific instructions; T-017 evidence |
| Unclear duration/codec metadata | Bad resume or unsupported playback | Provider/media engineer | Preserve unknown values; device capability and alternate-source UI |
| More native adapters than one developer can maintain | Schedule and reliability cost | Android lead | Select two proven launch sources; add others incrementally |
| Personal APK signing key lost | Cannot issue compatible updates | Release owner | Protected key backup and documented custody before release |
| No real device available | Media/performance claims unverified | Product/QA | Emulator work can proceed; physical-device gates remain open |

## 14. Completion evidence template

Copy one block per finished task or phase:

```text
Task/phase:
Status:
Owner:
Date:
Commit/build/version:
APK/checksum (if applicable):
Device and OS:
Source or controlled fixture:
Test procedure and command/report:
Expected result:
Observed result:
Evidence path/link:
Open issues and severity:
Next action:
```

Evidence paths should point to durable project artifacts or issue records, not only an ephemeral chat message. Use sanitized reports and recordings. Keep provider tokens and signed URLs out of evidence.

## 15. Change control and next action

If a confirmed requirement changes, update the decision register, affected PRD IDs, architecture, tasks and gates together. Re-estimate only affected work. Adding accounts, export, TV or public distribution does not silently expand the personal APK milestone.

Next implementation action: connect persisted addon providers to Home/Explore, then run T-003 live playback/download feasibility and continue the MovieBox/4KHDHub candidate evaluations. Controlled fixtures do not replace dated source and device evidence.
