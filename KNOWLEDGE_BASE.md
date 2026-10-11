# NiceTime — Project Knowledge Base

> **Purpose:** Read this instead of re-reading the whole repo.
> **Last updated:** 2026-10-11
> **Rule for agents:** when you change code or finish an investigation, append to §10 (Change log) and correct any section that became stale.

---

## 0. Git state (2026-10-11 — reconciled)

**The two lines were reconciled on 2026-10-11.** History: local `main` had diverged from
`origin/main` — origin held the playback fixes (`b99f42d`), the working tree held the UI redesign,
the Gradle 9.6/AGP 9.4/Kotlin 2.2.10 toolchain upgrade, and a second 4KHDHub rewrite (v2).

Reconciliation performed (committed and pushed to `origin/main` on 2026-10-11):
1. Stashed the working tree, fast-forwarded to `origin/main` (`44c09c4`) — pulled in the fixed
   `ScopedMediaClient` (PB-04/05), suspend+reason `PlaybackCommands`/`PlaybackError` (PB-06),
   the `ProviderRegistry` catalog gate (PB-07), MovieBox host failover (PB-12), typed 4KHDHub
   HTTP errors (PB-13), and the 5-test `ScopedMediaClientTest`.
2. Re-applied the stash, resolving 4 conflicts:
   - `MovieBoxProvider.kt` → **took origin** (strict superset: capability fix + host failover).
   - `FourKHDHubProvider.kt` + its test → **kept working-tree v2** (emulator-verified:
     Range-probe `verifyPlayableUrl`, per-host Referer, `elementBlocks` parser, ReleaseRef-keyed
     caches) and **ported origin's cold-resolve** into it: on a `mirrorUrls` miss, `resolve()`
     re-fetches the title page and re-parses instead of failing with "Release must be refreshed".
   - `MainActivity.kt` → **kept the UI redesign** and re-wired both play call sites to origin's
     suspend `play`/`playOffline` (null = ok, String = user-facing reason → `actionError` dialog);
     the initial history save moved inside the success branch so a refused play no longer records
     progress.
3. Verified after merge: `assembleDebug lintDebug testDebugUnitTest :core:model:test` →
   **green, 55 tests / 0 failures**, lint clean. The old stash was dropped after the push.

Everything below describes this reconciled tree.

---

## 1. What this project is

**NiceTime** — an Android (Kotlin + Jetpack Compose + Media3/ExoPlayer) movie & series streaming app.
Personal signed APK (no Play Store). YouTube-style browsing: **Home / Search / Downloads / You**.
Multi-source: two native providers (**MovieBox**, **4KHDHub**) + user-installed **Stremio HTTP addons** + a hardcoded **demo catalog**.

Scope is deliberately *not* social: no channels, comments, subscriptions, live TV (optional later).

---

## 2. Repo map

```
NiceTime/
├── KNOWLEDGE_BASE.md          ← this file (start here)
├── settings.gradle.kts        ← version catalog (libs.*) + module includes
├── build.gradle.kts           ← plugin versions (AGP/Kotlin/KSP)
├── gradle.properties          ← AGP 9 compatibility flags (see §3)
├── buildSrc/settings.gradle.kts ← DUPLICATE version catalog (subset) — keep in sync with settings.gradle.kts
├── android/                    ← ALL planning docs live here (not code)
│   ├── PRD.md, ARCHITECTURE.md, TASKS.md, PHASE_COMPLETION.md
│   ├── SOURCE_MATRIX.md       ← 4KHDHub now has one dated emulator PASS (2026-09-15)
│   ├── BUILD_AND_TEST.md      ← exact gradle/adb commands (⚠️ toolchain numbers are stale, trust §3)
│   ├── UI_prompt.md           ← the Netflix-inspired UI redesign brief that drove the current UI
│   ├── README.md              ← decision register
│   ├── evidence/
│   │   ├── media-device-test.txt                    ← FAIL: ExoPlaybackException (2026, older)
│   │   ├── 4khdhub-emulator-smoke-2026-09-15.txt    ← PASS: emulator search→details→1080p frame
│   │   └── 4khdhub-work-record-2026-09-15.md        ← what was done/verified that day
│   └── scripts/               ← Build-Release.ps1, Initialize-Signing.ps1
├── .github/workflows/android.yml   ← windows-latest, Temurin 17, sdkmanager android-36
├── app/
│   └── src/main/kotlin/app/nicetime/android/
│       ├── MainActivity.kt     ← 402 lines — nav shell only (tabs, NavHost, MediaController, demo titles)
│       ├── NiceTimeApplication.kt
│       └── ui/
│           ├── theme/          ← Color.kt, Type.kt, Shape.kt, Theme.kt (lavender accent design system)
│           ├── components/     ← Components.kt (cards/chips/skeletons/MiniPlayer/…), PlayerDialog.kt
│           └── screens/        ← HomeScreen, SearchScreen, DownloadsScreen, YouScreen,
│                                  SourceSettingsScreen, ProviderDetailsScreen, MovieDetailsScreen (DemoDetailsScreen)
└── core/
    ├── model/  (:core:model)  ← pure data contracts, no Android
    ├── data/   (:core:data)   ← providers, settings, history, addons
    └── media/  (:core:media)  ← ExoPlayer service, download engine, scoped HTTP
```

**Module graph:** `:app` → `:core:model`, `:core:data`, `:core:media` → `:core:model`.
`:core:data` does **not** depend on `:core:media`.

**Screenshots** (untracked, current UI): `nicetime_home_current.png`, `nicetime_explore_current.png`,
`nicetime_downloads_current.png`, `nicetime_you_current.png`, `nicetime_home_4khdhub_fixed.png`.
Also `android/logo.png`, `android/pages_ref.png` (UI brief mockups).

---

## 3. Toolchain & build

### Current (working tree, verified 2026-10-11)

| Item | Value |
|---|---|
| JDK | 17 (system install: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`, on PATH — no JAVA_HOME prefix needed) |
| Gradle | **9.6.0** (wrapper; dist pre-cached in `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.6.0-bin\`) |
| AGP | **9.4.0** |
| Kotlin | **2.2.10** (KSP 2.3.6) |
| Room | **2.7.2** |
| kotlinx-serialization-json | **1.6.3** (deliberately *downgraded* from 1.7.3 for Kotlin 2.2.10 compat) |
| Media3 | 1.4.1 |
| Coil | 2.7.0 (new — poster/backdrop images) |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| appId | `app.nicetime.android` (debug suffix `.debug`) |

- `local.properties` → `sdk.dir=C:/Users/Rama-office/AppData/Local/Android/Sdk` (system SDK, not the old portable tooling).
  Platform 36 + build-tools 34.0.0/35.0.0/36.0.0 installed. AVD `NiceTime_API_35` exists.
  `adb` is **not on PATH** — use `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- `gradle.properties` carries AGP-9 compatibility flags (`android.builtInKotlin=false`,
  `android.newDsl=false`, `android.enableAppCompileTimeRClass=false`, etc.). Do not remove without
  re-testing the build; they exist because the project predates AGP 9's defaults.
- ⚠️ The version catalog is declared **twice**: `settings.gradle.kts` (full) and
  `buildSrc/settings.gradle.kts` (subset, no DASH/database/okhttp-media3). Editing only one causes
  confusing resolution drift. `buildSrc/settings.gradle.kts` also still declares
  `kotlinx-serialization-json:1.6.3`.

```powershell
# Build + unit tests + lint (verified green 2026-10-11: 53 tests, 0 failures, lint clean)
.\gradlew.bat assembleDebug testDebugUnitTest :core:model:test lintDebug '-Pkotlin.compiler.execution.strategy=in-process'

# Instrumentation (needs device/emulator; adb not on PATH)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat :app:assembleDebugAndroidTest
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w app.nicetime.android.debug.test/app.nicetime.android.MediaAcceptanceInstrumentation
```

**Unit test counts (reconciled tree, 2026-10-11):** 55 total — FourKHDHub 11, RegressionSuite 9,
ProviderRegistry 7, MovieBox 5, AddonClient 4, ScopedMediaClient 5,
Diagnostics 2, StremioAddon 2, ContentProviderContract 4, Identity 4, DownloadIdentity 2.
`:app` still has **no unit tests** (`testDebugUnitTest NO-SOURCE`); `:app` coverage is only the
instrumentation suite. APK ≈ 22 MB (grew from 15.5 MB — the new launcher icons are ~1 MB PNGs
each, same image in every density bucket; re-export proper densities to shrink it).

### Networking (unchanged, still true)
- A local HTTP proxy was seen at `127.0.0.1:10809` in a previous environment but is **not listening** here.
- Direct HTTPS works to `dl.google.com`, `repo.maven.apache.org`, `plugins.gradle.org`, `maven.google.com`.
  Do **not** set proxy `systemProp.*` in `gradle.properties`.
- Dependency failures surface as a useless NPE:
  `Cannot invoke "java.util.List.get(int)" because "path" is null` — that is *always* a resolution
  failure, not a code bug. Diagnose with `--debug` + `Select-String "Could not resolve|Caused by:"`.

### PowerShell gotchas (still true)
- `java -version` / `sdkmanager` write to **stderr**; `$ErrorActionPreference='Stop'` turns that into a
  terminating error. Capture with `try { ... 2>&1 | Out-String } catch {}`.
- Use forward slashes in `local.properties` — Java accepts them, backslash-escaping is easy to get wrong.
- **Don't `gradlew --stop` a build you still want.**

---

## 4. Runtime architecture

### 4.1 Playback pipeline (the critical path)

```
UI (a screen) ─ onPlay(stream) ─→ PlaybackCommands.play(mediaController, stream, positionMs)
      └─ sendCustomCommand(PLAY_RESOLVED, bundle{uri,mime,headers,hosts,mediaId,position})
            └─ NiceTimePlaybackService.onCustomCommand
                  └─ ResolvedMediaSourceFactory.create(...)
                        └─ scopedMediaClient(uri, headers, allowedHosts)   ← OkHttp w/ host allow-list
                        └─ OkHttpDataSource → DefaultMediaSourceFactory
                  └─ player.setMediaSource(source, position); prepare(); play()
```

- `NiceTimePlaybackService : MediaSessionService` — owns the process-wide `ExoPlayer` + `MediaSession`.
  `onConnect` rejects foreign packages. Commands: session defaults + `PLAY_RESOLVED` + `PLAY_OFFLINE`.
- `NiceTimeDownloadService : DownloadService` (id 3102).
- `MediaController` is built once in `MainActivity.NiceTimeApp` (line ~92) and passed into screens.
- Demo titles bypass the scoped client — `DemoDetailsScreen`/`MovieDetailsScreen.onPlay` calls
  `player.setMediaItem(MediaItem.fromUri(...))` directly.
- `PlaybackCommands.play/playOffline` are **suspend** and return `null` (ok) or a user-facing
  reason; the redesigned `MainActivity` launches them in `scope` and shows a refusal in the
  `actionError` dialog (PB-06, fixed at the reconciliation).

### 4.2 UI structure (as of the 2026-09-15 redesign)

- `MainActivity.kt` (402 lines): `MainActivity` → `NiceTimeApp` composable. Owns tabs
  (`MainTab`: HOME `home`, SEARCH `search`, DOWNLOADS `downloads`, YOU `you`), `NavHost`, the
  `MediaController`, `MiniPlayer` + `NavigationBar` scaffold, watch-progress persistence loop, and
  the `DisplayTitle`/`ProviderTitle` wrappers + `demoTitles` list.
- Navigation routes: `home`, `search`, `downloads`, `you`, `source_settings`,
  `demo_detail/{titleId}`, `provider_detail/{titleName}/{sourceName}`.
  ⚠️ The provider route is still **keyed by title string** and re-searched on open (PB-10, open).
- `ui/theme/`: dark lavender design system — `Color.kt` (Background `#0A0A0F`, Accent `#B4A0E5`,
  …), `Type.kt`, `Shape.kt`, `Theme.kt` (explicit dark + light `ColorScheme`).
- `ui/components/Components.kt`: `NiceTimeTopBar`, `HeroBanner`, `SectionHeader`, `MoviePosterCard`,
  `LandscapeCard`, `SearchResultCard`, `GenreChip`, `PreferenceSwitch`, `EmptyState`, `ErrorState`,
  `LoadingSkeleton`, `MiniPlayer`, `SourceCard`, `DownloadItemCard`.
  `PlayerDialog.kt`: Compose `Dialog` wrapping `androidx.media3.ui.PlayerView`. Images via Coil `AsyncImage`.
- `ui/screens/`: `HomeScreen`, `SearchScreen` (Explore was renamed to Search), `DownloadsScreen`,
  `YouScreen` (settings + diagnostics + history), `SourceSettingsScreen`,
  `ProviderDetailsScreen` (297 lines; re-searches by title, synthesises a movie `EpisodeRef` when
  `type == MOVIE`), `MovieDetailsScreen.kt` → contains **`DemoDetailsScreen`** (filename ≠ composable).

### 4.3 Demo catalog (always works offline-ish; used for testing)

`MainActivity.kt:75-80` — 4 hardcoded titles (id, title, year, kind, synopsis, accent colour, HTTPS HLS URL):
`bbb` Big Buck Bunny, `sintel` Sintel, `tears` Tears of Steel, `cosmos` Cosmos Laundromat —
same mux.dev / Apple devstreaming test streams as before.

### 4.4 Downloads

`DownloadRuntime` (object, `core/media/DownloadRuntime.kt`) — unchanged:
- `SimpleCache` in `getDir("offline_media")` with `NoOpCacheEvictor` + per-item cache key `$id|$key`.
- `enqueue()` via `DownloadHelper.forMediaItem` → `DownloadService.sendAddDownload`.
- Request context stored **encrypted** via `DownloadSecrets` bound to `CredentialStore`.
- `offlineSource()` plays **only** completed downloads, no network fallback.

### 4.5 Providers (`core/data`)

| Provider | Type | Enabled by default | Notes |
|---|---|---|---|
| **MovieBox** | signed REST client (HMAC-MD5 + guest JWT) | ✅ | DASH/CloudFront signed URLs; `catalog = true` now claimed; ⚠️ working tree still has pre-fix 401/403 failover + hardcoded Referer (see §6) |
| **4KHDHub** | HTML scraper (`https://4khdhub.one/`) | ✅ | Working-tree rewrite: real `catalog()` from homepage, `elementBlocks` div-stack parser, mirror resolution HubCloud→HubDrive→pixeldrain→direct, **1-byte Range probe** (`verifyPlayableUrl`) before handing a URL to the player, 120 s `expiresAtEpochMs`, per-host `Referer` for `*.workers.dev` |
| **Stremio addon** | user-supplied HTTPS manifest | user installs | real URLs but **no headers** passed (PB-09, open) |
| Demo | hardcoded | always | always plays |

- `NativeProviders.movieBox` / `.fourKHDHub` are process-wide `by lazy` singletons.
- All `resolve()` implementations are **cache-first**; 4KHDHub's working-tree `resolve()` returns
  `NotFound("Release must be refreshed")` unless `streams()` populated `mirrorUrls` in this process
  (cold resolve after process death fails — the origin version re-fetches the page instead).
- 4KHDHub `resolveMirror` chain: `hubcloud.*` → resolver page → pixeldrain API URL; `hubdrive.*` →
  hubcloud; else `pixeldrainApiUrl(url) ?: validatePlaybackUrl(url)`. `validatePlaybackUrl` requires
  HTTPS + a media extension (or pixeldrain API path) and rejects localhost/.zip/login.

---

## 5. Key data model (core/model)

```kotlin
MediaRef(providerInstanceId, providerMediaId, type: MediaType)   // MOVIE | SERIES
EpisodeRef(media: MediaRef, providerEpisodeId, season: Int?, episode: Int?)
ReleaseRef(episode: EpisodeRef, providerReleaseId, label, resolutionHeight, audioLanguage, container, downloadable)
ResolvedStream(release, uri, mimeType?, requestHeaders, allowedHosts, // empty = origin host only
               expiresAtEpochMs?,   // NOW written by 4KHDHub (120 s TTL); still unread by MovieBox
               subtitles, sourceLabel)
MediaSummary / MediaDetails / Season / EpisodeSummary / Release
ProviderResult<T> = Ok<T> | Failure(ProviderFailure)
ProviderFailure = Timeout | NetworkUnavailable | RateLimited | AuthExpired | NotFound | Unsupported | ParseFailure | SourceUnavailable
ProviderCapabilities(catalog, search, pagination, streamResolution, downloadable, externalSubtitles, supportedTypes, liveTv)
```

`ContentProvider` interface: `instanceId`, `capabilities`, `catalog()`, `search()`, `details()`,
`streams()`, `resolve()`, `subtitle()`.

---

## 6. Known defects

> IDs are stable; reference them in commits. Status reflects the **reconciled tree** (§0).

### Playback blockers — all fixed as of the reconciliation

| ID | Defect | Status |
|---|---|---|
| **PB-04** | Host allow-list killed CDN redirects/segments (`followSslRedirects(false)`, hard IOException off-scope). | ✅ Fixed — origin's `ScopedMediaClient` follows cross-host hops credential-free, refuses HTTPS→HTTP downgrade. |
| **PB-06** | Silent failure on start refusal. | ✅ Fixed — `PlaybackCommands.play/playOffline` are suspend and return a user-facing reason; UI shows it in the `actionError` dialog (rewired in the redesigned `MainActivity`). |
| **PB-07** | No `capabilities.catalog` gate → red error on Home for catalog-less providers. | ✅ Fixed — origin's `ProviderRegistry` gate restored. |
| **PB-12** | MovieBox aborted host failover on first 401/403. | ✅ Fixed — origin's `MovieBoxProvider` (kept wholesale in the merge). |
| **PB-13** | 4KHDHub `fetchText` returns `null` on non-2xx → misreported as `NetworkUnavailable`, hiding 403/404/Cloudflare. | **Open** — v2 kept the null-return (`FourKHDHubProvider.kt:136`); origin's typed `HttpStatusException` mapping was not ported. |

### High risk / correctness

| ID | Defect | Status |
|---|---|---|
| PB-08 | `expiresAtEpochMs` never read by MovieBox (CloudFront signed URLs go stale; cold MovieBox resolve after process death fails). 4KHDHub writes a 120 s TTL and (since the merge) cold-resolves by re-fetching the page. | Partially fixed (4KHDHub yes, MovieBox open) |
| PB-09 | Stremio addon streams carry no `requestHeaders`; `behaviorHints` never read → addons needing `Referer`/`Origin` 403. | Open (out of scope per user) |
| PB-10 | Discovery navigates by **title string** (`provider_detail/{titleName}/{sourceName}`) and re-searches (`ProviderDetailsScreen.kt:48-67`, `items.firstOrNull()`) → can open the wrong movie. Violates the project's own `Identity.kt` rule. | Open |
| PB-11 | MovieBox hardcodes `Referer: https://sportslive.wine` on every stream. | Open (`MovieBoxProvider.kt:152`) |
| PB-17 | `ProviderRegistry.fetchNextPage` uses the **cursor as the search query** (`SearchRequest(query=cursor)`); for MovieBox the cursor is a page number → would search for `"2"`. | Open (`ProviderRegistry.kt:136`), currently unreachable from the UI |
| PB-18 | `MovieBoxProvider.http` (OkHttp, `Proxy.NO_PROXY`) is unused; transport is `HttpURLConnection`. Harmless dead field. | Open (cleanup) |
| PB-21 | All five launcher mipmaps contain the **same ~1 MB PNG** (identical byte size). Bloats the APK to ~22 MB and wastes memory at low densities. | Open (new) |
| — | `android/BUILD_AND_TEST.md` ends with a stray line `opencode -s ses_…` (accidental append). Toolchain versions in that file (Gradle 8.10.2 / AGP 8.6.1) are stale. | Open (doc drift) |

### Fixed in earlier sessions (both lines of work, now merged)

PB-01 (4KHDHub resolve re-fetches the page on demand — origin's approach ported into v2 at merge),
PB-02 (deterministic release ids / caller EpisodeRef preserved by v2's `parseReleases`),
PB-03 (series episodes keep real media id), PB-14 (mirror dedup — v2's keyed merge),
PB-15 (non-truncating item parsing — v2's `elementBlocks` div-stack), PB-16 (capability flags honest
on both natives), PB-19 (movies get a selectable row — v2 at the provider level + `ProviderDetailsScreen`
synthesising the movie `EpisodeRef`), PB-20 (API-33 `URLDecoder` crash — the `Uri` helper at
`MainActivity.kt:399`).

### Evidence status (2026-10-11)

- ✅ **`android/evidence/4khdhub-emulator-smoke-2026-09-15.txt`** — 4KHDHub **PASS** on the API 35
  emulator: search "Inception" → details → 7 releases → Play 1080p H.264 → player renders a frame;
  Home Trending shows a live 4KHDHub card. Mirror returned HTTP 206 / Matroska with resolver Referer.
  *Not exercised:* full-length playback, physical phone UI check (phone was locked; APK install
  succeeded), offline completion, URL expiry, other codecs/mirrors.
- ❌ `android/evidence/media-device-test.txt` — old `FAIL: ExoPlaybackException: Source error`.
- All 26 acceptance rows AT-01..AT-26 in `PHASE_COMPLETION.md` remain **"Not run"**.
- `SOURCE_MATRIX.md` updated: 4KHDHub has the emulator check but is **not** yet a verified launch
  source (no phone playback, no offline/sustained evidence). MovieBox still has zero live evidence.

---

## 7. Docs index — where to look

| Question | File |
|---|---|
| What must the app do? | `android/PRD.md` (FR-/NFR- IDs) |
| How should it be built? | `android/ARCHITECTURE.md` |
| What's the task status? | `android/TASKS.md` (T-0xx) — *boxes are stale, trust the status lines* |
| What's blocking release? | `android/PHASE_COMPLETION.md` |
| Which sources work? | `android/SOURCE_MATRIX.md` |
| How do I build/test? | `android/BUILD_AND_TEST.md` (⚠️ stale toolchain numbers — use §3) |
| Why was a decision made? | `android/README.md` § decision register |
| Why does the UI look like this? | `android/UI_prompt.md` + `android/pages_ref.png` |
| What's proven? | `android/evidence/` (1 emulator PASS, 1 older FAIL) + `4khdhub-work-record-2026-09-15.md` |

**Version catalog:** `settings.gradle.kts` (authoritative) + the duplicate subset in
`buildSrc/settings.gradle.kts`.

---

## 8. Conventions & gotchas

- `MainActivity.kt:391` defines a **`private object Uri`** (URLEncoder/URLDecoder wrapper) — *not*
  `android.net.Uri`. Don't "fix" it as a missing import. It also exists to avoid the API-33-only
  `URLDecoder.decode(String, Charset)` overload (minSdk 26) — PB-20.
- `@UnstableApi` (Media3) is used broadly; opt in via `@OptIn(UnstableApi::class)`. Only annotate
  declarations that actually touch Media3.
- Cleartext HTTP is **blocked** except debug-to-localhost; all known endpoints are HTTPS.
- Media3 HLS/DASH modules are `implementation` deps of `:core:media` (needed by
  `DefaultMediaSourceFactory` reflection).
- Tests: unit tests under each module's `src/test/kotlin`; instrumentation
  `app/src/androidTest/.../MediaAcceptanceInstrumentation.kt` (MockWebServer + `sample.mp4`).
- `PHASE_COMPLETION.md` §3 is the project's "definition of done".
- 4KHDHub HTML parsing rules that matter: `elementBlocks()` keeps nested divs together so a file
  title and its mirror links stay in one item; season/episode come from `SxxEyy` in the filename;
  movie pages have no `episode-*` markup, so the **UI** synthesises the single movie episode
  (`ProviderDetailsScreen.kt:57-58`).
- `resolveMirror` is a **public** function on `FourKHDHubProvider` (used by tests/diagnostics).

### 8.1 Compile traps (all hit in this project's history)

| Trap | Wrong | Right |
|---|---|---|
| `resume` resolves to an internal coroutines symbol | `import kotlinx.coroutines.resume` | `import kotlin.coroutines.resume` |
| `SessionResult` has no `(Int, Bundle)` ctor | `SessionResult(code, bundle)` | `SessionResult(SessionError.ERROR_BAD_VALUE)` |
| Accidentally nesting a `ListenableFuture` | re-wrapping an already-future in `Futures.immediateFuture` | return the inner future directly |
| API-33-only JDK overloads | `URLDecoder.decode(s, Charset)`, `URLDecoder.decode(s, StandardCharsets.UTF_8)` | `URLDecoder.decode(s, "UTF-8")` (API 1) — lint (`lintDebug`) catches this; **always run lint with assemble** |
| Lint scans `local.properties` | unescaped drive colon → `PropertyEscape` error | `C\:/Users/...` (gitignored but breaks `lintDebug`) |

### 8.2 Lint is part of the build

`lintDebug` **aborts the build** on errors and is the only thing that catches API-level mistakes.
Always run `assembleDebug lintDebug testDebugUnitTest` together — a green `assembleDebug` alone
does not mean the APK runs on a minSdk-26 device.

---

## 9. Decisions recorded from the user

1. **Sources in scope:** MovieBox **and** 4KHDHub (the two native providers). Stremio addons and the
   demo catalog are not the priority.
2. **Observed symptom (historic):** "Playback failed. Retry or choose another source." → the
   host-allow-list / redirect refusal (PB-04/PB-05), not a codec problem.
3. **"Make it dynamic" means:** *show only content that can actually be played* — filter out
   releases/titles/sources known to fail; offer a real reason when nothing is playable.
4. **UI direction:** Netflix-*inspired* (not a clone) cinematic dark UI with NiceTime's own identity
   and lavender accent — see `android/UI_prompt.md`. Preserve all backend/playback functionality.
5. **Release signing:** personal RSA-3072 keystore at `C:\Users\Rama-office\.nicetime\signing\`
   (DPAPI-protected password, env-var passing). Do not regenerate.

---

## 10. Change log

Append one line per session. Format: `YYYY-MM-DD — what changed — sections updated`.

- 2026-10-11 — Initial knowledge base created from full code + docs audit. Found 18 playback defects (PB-01..PB-18). — §§1-9
- 2026-10-11 — Installed portable JDK 17 + Android SDK (platform 36); documented PowerShell/Gradle gotchas. — §3
- 2026-10-11 — Fixed the "Playback failed" root cause: scoped HTTP client follows cross-host CDN/segment redirects while stripping credentials; HTTPS→HTTP downgrade still refused. — §6, §4.1
- 2026-10-11 — Made start failures visible: `PlaybackCommands.play/playOffline` became suspend + reason-returning. — §4.1
- 2026-10-11 — Rewrote the 4KHDHub path: real `catalog()`, deterministic release ids, caller identity preserved, dynamic re-resolution on Play, typed HTTP error mapping, non-truncating item parsing. — §4.4
- 2026-10-11 — MovieBox: host failover no longer aborts on the first 401/403; capability flags now tell the truth. — §4.4
- 2026-10-11 — UI: releases matched by media + season/episode; `Unsupported` no longer shown as a red error on Home. — §6
- 2026-10-11 — Fixed 4KHDHub movies (no selectable row) and the API-33 `URLDecoder` crash on minSdk-26. — §6, §8.2
- 2026-10-11 — Verified green: `assembleDebug lintDebug testDebugUnitTest`, lint clean. All of the above was **committed to `origin/main`** as `b99f42d`. — §3, §6
- 2026-10-11 — **KB re-sync with the working tree.** Discovered the working tree is a separate, uncommitted line of work (UI redesign into `ui/screens`+`ui/components`+`ui/theme`, toolchain jump to Gradle 9.6/AGP 9.4/Kotlin 2.2.10/Room 2.7.2, a second 4KHDHub rewrite with Range-probe verification, Coil images, new launcher icons, 2026-09-15 emulator evidence) that **does not contain the `origin/main` playback fixes**. Recorded the divergence in §0, rewrote §2 (repo map), §3 (toolchain + verified test counts 53/0), §4 (UI structure, provider behaviour), §6 (per-tree defect status incl. new PB-21 icon bloat), §8, §9. — all sections
- 2026-10-11 — **Reconciled the two lines.** Stash → ff to `origin/main` → pop with 4 conflicts resolved (MovieBox=origin, 4KHDHub+test=working-tree v2, MainActivity=UI redesign rewired to the suspend play API; origin's cold-resolve ported into v2). Build green: 55 tests / 0 failures, lint clean. PB-04/05/06/07/12 now fixed in the tree; PB-08 fixed for 4KHDHub; PB-13 remains open. Committed and pushed to `origin/main`; stash dropped. — §§0, 3, 4.1-4.2, 6
