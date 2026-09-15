# Product requirements: NiceTime

Date: 2026-09-14. Status: detailed proposal incorporating confirmed user preferences in [the decision register](README.md#decision-register). Related: [architecture](ARCHITECTURE.md), [tasks](TASKS.md), [phase gates](PHASE_COMPLETION.md).

## 1. Product outcome

Let a viewer discover a movie or series, select an available source, start watching, and download selected content for reliable offline viewing. The experience should feel familiar to a YouTube user while representing movie and episode information accurately.

The user explicitly requires Kotlin, Android, streaming, downloads, detailed planning, and scalability. The user confirmed YouTube-style browsing and playback for movies/series only. Creator uploads, channels, comments, public subscriptions, and a social graph are excluded.

The app is named **NiceTime**. The first distribution is a personal APK. Downloads are for in-app offline viewing. No account or owned backend is required initially; optional accounts may be integrated later. Multiple providers are required, with the exact launch roster selected through compatibility tests rather than assumed availability.

## 2. Audience and jobs

| Viewer | Need | Successful outcome |
| --- | --- | --- |
| Casual viewer | Find something quickly without understanding providers | Search to first frame through clear choices |
| Series viewer | Continue the right episode and retain progress | Resume correct episode and timestamp |
| Commuter | Watch without a stable connection | Completed downloads play after restart in airplane mode |
| Multi-language viewer | Select audio and subtitles | Available tracks shown accurately; preference remembered |
| Advanced viewer | Configure personal addons or TV playlists | Install, enable, disable, and remove sources without rebuilding app |

## 3. Scope and release boundaries

### Initial beta: Must

- Guest access, original app shell, provider setup, movie/series discovery, search, details, and episodes.
- At least one verified HTTP addon path and one known-good test catalog.
- At least two independent working content sources in the personal beta, exercised through the same interface. HTTP addons, MovieBox and 4KHDHub are launch candidates. A controlled fixture is not a production source; duplicate configurations of one upstream do not count as two independent sources.
- Integrated player with seeking, quality/audio/subtitle selection when available, resume, mini-player, and picture-in-picture on supported devices.
- Persistent download queue, selected tracks, offline playback, storage management, pause/resume, retry, and deletion.
- Local watchlist, history, continue watching, preferences, source-specific failures, and accessibility.
- Performance, recovery, and release checks defined below.

### After beta: Should

- Further native adapters beyond the verified launch roster; optional BDIX sources.
- Batch episode downloads and improved discovery. Accounts and file export are later optional extensions.
- Additional UI languages based on target users.
- M3U live TV only if requested as a scope extension; it is not a movie/series beta dependency.

### Separately scoped: Later

- Android TV, casting, creator uploads, live broadcasting, comments, social subscriptions, billing, ads, DRM offline licenses, and machine-learning recommendations.
- A server-owned catalog, transcoding pipeline, CDN delivery, or shared server-side source resolution.

Creator-platform features are outside the confirmed scope. Any future request for uploads or social features requires separate requirements and estimates.

## 4. Experience and information architecture

Phone bottom navigation: **Home / Explore / Downloads / You**. Search is available in the top bar. Explore offers movie and series destinations. Live TV appears only if the optional extension is selected. Source management belongs in Settings with a short first-run setup path.

| Screen | Content and actions | Required states |
| --- | --- | --- |
| First run | App introduction, source setup, network/download preferences, skip to available demo content | No sources, invalid manifest, setup success |
| Home | Continue watching, source catalog shelves or feed, category chips, title cards | Loading, populated, empty, stale offline cache, partial source failure |
| Search | Debounced query, recent local queries, type/language/source filters, paged results | Typing, loading, results, no results, source timeout |
| Details/watch page | Artwork, title/year, synopsis, seasons/episodes, Play, Download, Watchlist, related items | Missing metadata, no playable release, retry, unavailable episode |
| Source sheet | Provider, resolution, audio, codec/container, size if known, availability | Resolving, results, unknown fields, all sources failed |
| Player | Video, title, timeline, play/pause, seek, tracks, fit/fullscreen, episode navigation | Preparing, playing, paused, buffering, ended, recoverable/fatal error |
| Mini-player | Current title, thumbnail/video surface, play/pause, dismiss, expand | Navigation retains position and session |
| Downloads | Queued/active/completed items, byte progress, pause/resume/retry/delete, space summary | Waiting for Wi-Fi, low space, authentication expired, failed, offline ready |
| You/library | Watchlist, history, clear history, settings, future optional account entry | Empty, local data, unavailable saved source |
| Live TV (optional extension) | Playlist selection, channel search, groups, favorites | No playlist, refresh error, unavailable channel |

Visual direction: 16:9 feed cards where suitable artwork exists, poster cards for movie shelves, strong title hierarchy, compact secondary metadata, consistent rounded surfaces, light/dark themes, and a persistent player transition. Never stretch portrait artwork into fake landscape art. Do not fabricate views, likes, creator names, ratings, or upload dates when providers do not supply them. Do not add a nonfunctional Shorts/Create/Subscriptions tab.

Navigation: back exits fullscreen before leaving the watch page; leaving the page collapses into mini-player; dismiss stops playback. Returning restores scroll position and selected episode. Confirm before destructive batch deletion, not routine navigation.

## 5. Functional requirements and acceptance criteria

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-01 | Must | Guest setup and preferences | Fresh install can reach discovery without login; no-source state explains source setup; settings survive restart |
| FR-02 | Must | Source management | Validate manifest URL/capabilities; enable/disable/remove; keep credentials out of displayed URLs; unavailable source does not crash other sources |
| FR-03 | Must | Home discovery | Paginate catalogs; restore feed position; show stale label offline; continue watching above discovery when available |
| FR-04 | Must | Search | Debounce around 300 ms; cancel stale queries; display incremental source results and per-source errors; no stale result overwrites newer query |
| FR-05 | Must | Details and episode identity | Correct title, media type, season/episode and provider ID retained through play/download; missing metadata has useful fallbacks |
| FR-06 | Must | Release selection | Show only supported playable candidates; unknown quality stays unknown; changing episode invalidates old releases; expose alternate sources |
| FR-07 | Must | Streaming | Play verified MP4/HLS/DASH samples; seek and recover from short disconnect; required headers reach correct authorized hosts; unsupported codec has actionable error |
| FR-08 | Must | Player navigation | Mini-player, fullscreen, rotation and PiP preserve one playback session; audio focus and headset removal behave correctly; background behavior follows preference |
| FR-09 | Must | Tracks and preferences | Select available audio/subtitle tracks; choose adaptive Auto or supported quality; fixed releases require re-resolution; unavailable tracks are not promised |
| FR-10 | Must | History and watchlist | Save progress at intervals and lifecycle transitions; resume within 5 seconds of persisted point; remove history and bookmark; history opt-out prevents new records |
| FR-11 | Must | Download selection and queue | Select episode/release/tracks; deduplicate same request; queue survives process death; Wi-Fi-only setting applies; display actual bytes or indeterminate progress |
| FR-12 | Must | Download recovery | User pause persists; restart/reboot recovery respects OS rules; distinguish waiting/error; expired authorization has bounded refresh; changed media cannot corrupt old partial download |
| FR-13 | Must | Offline library | Completed media and selected subtitles play after process restart in airplane mode with network access disabled; deletion frees content; online cache cleanup cannot evict downloads |
| FR-14 | Must | Storage controls | Show used/free storage; prevent known oversized downloads with reserve; stop cleanly on unknown-size exhaustion; app uninstall consequence explained in download settings |
| FR-15 | Later | Live TV, optional extension | Import URL/local M3U, parse groups/logos, deduplicate channels and play supported streams; no VOD seek/download promise for live streams |
| FR-16 | Must | Accessibility and adaptive layouts | TalkBack labels and logical focus; 48 dp touch targets; 200% font size on key screens without clipped actions; phone/tablet and landscape checks |
| FR-17 | Must | Diagnostics and error recovery | Error includes source and retry/alternate action; local export redacts credentials and raw signed URLs; user can clear diagnostics |
| FR-18 | Must | Multiple providers | At least two independent sources pass search/details/episode/play/download contracts where supported; one broken source does not block launch or other sources; publish individual capabilities |
| FR-19 | Later | Batch and optional export | Batch actions show count/estimated size; direct-file export is explicit and cancellable; adaptive offline cache is not labeled as a standalone MP4 |
| FR-20 | Later | Accounts and sync | Guest remains usable; merge local library on opt-in login; deletions propagate; sign-out isolates account data; no credential or download-byte sync |

## 6. Core journeys

### Discover and watch

1. Open Home; render local shell and cached metadata immediately where available.
2. Search or choose a title, then an episode for series.
3. Resolve available releases. If no default is known, show a concise source/quality sheet.
4. Start playback; preserve required request context and selected tracks.
5. Browse while mini-player continues, expand again, then leave and resume later.
6. If source fails, preserve episode/position and offer retry or another release. Cross-source resume is best effort because cuts/durations may differ.

### Download and watch offline

1. Select Download, quality/audio/subtitles and network preference.
2. Show size estimate when available, storage availability and queue entry.
3. Download continues through app backgrounding when Android permits, with visible system controls.
4. On completion mark Offline ready only after required assets are present.
5. Airplane-mode playback reads local media; missing tracks explain their absence without quietly fetching online.
6. Delete removes download content without removing watchlist/history unless separately requested.

### Recover a failed source

Source-specific error preserves search/details context. A temporary outage allows retry after bounded backoff. Missing content offers another release; invalid credentials return to source settings. Permanently unsupported formats stop with a clear message rather than looping.

## 7. Nonfunctional targets

All targets are proposed release gates, not measured results. Record device, OS, build, thermal state, network and dataset with evidence.

| ID | Target | Measurement |
| --- | --- | --- |
| NFR-01 | Cold start to interactive shell p95 <= 2.5 seconds | 30 release-build launches on designated midrange physical reference phone; excludes remote catalog completion |
| NFR-02 | First frame p95 <= 3 seconds after a valid URL is available | Controlled 1080p H.264 source on 20 Mbps / 50 ms RTT; report source-resolution latency separately |
| NFR-03 | Resolution p95 <= 10 seconds on controlled addon fixture; 15-second interactive overall deadline | At least 100 requests with partial failures; external provider timing reported separately |
| NFR-04 | >= 99.5% crash-free sessions in beta | Opt-in measurement if enabled; report denominator; fewer than 1,000 sessions is insufficient reliability evidence |
| NFR-05 | 100% pass of required offline/recovery acceptance scenarios | Device matrix in phase gates; no claim of uninterrupted execution after force-stop |
| NFR-06 | Feed missed-frame ratio < 5% on reference device | Repeatable release-build scroll benchmark with 1,000 cached cards and bounded image memory |
| NFR-07 | Bounded resource usage | One active player, 4 interactive provider requests globally, 2 per host, 2 downloads initially; profile 30-minute session for leaks |
| NFR-08 | Data correctness | Migration fixtures retain history/watchlist; duplicate titles across providers do not merge without verified external identity |
| NFR-09 | Credential isolation | No credentials in logs/backups/analytics; request-specific headers cannot bleed into another provider's traffic |

## 8. Analytics and product measurement

Start with local diagnostics. Remote analytics are optional and require a product decision. Event proposals: discovery opened, search completed, resolution outcome, playback first frame, rebuffer duration, download state change, offline playback outcome. Use opaque session/source identifiers, bucketed durations and error categories. Exclude search text, title names, account tokens and full URLs by default.

Primary funnel: discovery -> details -> valid source -> first frame -> meaningful playback. Measure download requested -> completed -> successful offline open separately. High provider failure rates must be attributed by provider rather than hidden in overall averages. Retention targets should be set after a real beta baseline.

## 9. Dependencies, uncertainty, and rollout

- Working source documentation does not establish live availability, stable domains, compatible codecs, or permission to distribute a catalog. P0 tests the chosen launch sources and determines what content the product offers.
- YouTube familiarity describes interaction design; it does not imply a YouTube-scale hosting system or cloned branding.
- Provider integration can dominate schedule; keep the controlled media fixture independent of external uptime.
- Downloads and background work are constrained by Android versions, power policy and device vendors. Display recoverable states accurately.
- Signed personal APK is the selected release channel. Signing ownership, upgrade behavior, source roster and known limitations are recorded with that APK. Public distribution requires a later, separate release plan.

See [phase completion](PHASE_COMPLETION.md) for blockers and evidence before release.
