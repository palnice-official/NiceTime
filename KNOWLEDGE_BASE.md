# NiceTime — Project Knowledge Base

> **Purpose:** Read this instead of re-reading the whole repo.
> **Last updated:** 2026-10-11
> **Rule for agents:** when you change code or finish an investigation, append to §10 (Change log) and correct any section that became stale.

---

## 1. What this project is

**NiceTime** — an Android (Kotlin + Jetpack Compose + Media3/ExoPlayer) movie & series streaming app.
Personal signed APK (no Play Store). YouTube-style browsing: **Home / Explore / Downloads / You**.
Multi-source: two native providers (**MovieBox**, **4KHDHub**) + user-installed **Stremio HTTP addons** + a hardcoded **demo catalog**.

Scope is deliberately *not* social: no channels, comments, subscriptions, live TV (optional later).

---

## 2. Repo map

```
NiceTime/
├── KNOWLEDGE_BASE.md          ← this file (start here)
├── settings.gradle.kts        ← version catalog (libs.*) + module includes
├── build.gradle.kts, gradle.properties, gradlew.bat
├── nicetime_home.png          ← screenshot of Home
├── android/                    ← ALL planning docs live here (not code)
│   ├── PRD.md                 ← requirements FR-01..FR-20, NFR-01..NFR-09
│   ├── ARCHITECTURE.md        ← target architecture ("partially implemented")
│   ├── TASKS.md               ← T-001..T-042 backlog (all boxes [x] but re-opened by audit)
│   ├── PHASE_COMPLETION.md    ← P0..P7 gates, AT-01..AT-26 acceptance matrix (ALL "Not run")
│   ├── SOURCE_MATRIX.md       ← what sources are actually verified (answer: none live)
│   ├── BUILD_AND_TEST.md      ← exact gradle/adb commands
│   ├── README.md              ← decision register
│   ├── evidence/              ← only file: media-device-test.txt = "FAIL: ExoPlaybackException: Source error"
│   └── scripts/               ← Build-Release.ps1, Initialize-Signing.ps1
├── .github/workflows/android.yml   ← windows-latest, Temurin 17, sdkmanager android-36
├── buildSrc/                  ← empty helper (no version logic)
├── app/                       ← :app (application module)
│   └── src/main/kotlin/app/nicetime/android/
│       ├── MainActivity.kt     ← 583 lines — ALL UI lives here (screens + nav + player)
│       ├── NiceTimeApplication.kt
│       └── ui/theme/Theme.kt
└── core/
    ├── model/  (:core:model)  ← pure data contracts, no Android
    ├── data/   (:core:data)   ← providers, settings, history, addons
    └── media/  (:core:media)  ← ExoPlayer service, download engine, scoped HTTP
```

**Module graph:** `:app` → `:core:model`, `:core:data`, `:core:media` → `:core:model`.
`:core:data` does **not** depend on `:core:media`.

---

## 3. Toolchain & build

| Item | Value |
|---|---|
| JDK | 17 |
| Gradle | 8.10.2 (wrapper) |
| AGP | 8.6.1 |
| Kotlin | 2.0.20 |
| Media3 | 1.4.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| appId | `app.nicetime.android` (debug suffix `.debug`) |

```powershell
# Build + unit tests + lint (from BUILD_AND_TEST.md:11)
.\gradlew.bat assembleDebug testDebugUnitTest :core:model:test lintDebug '-Pkotlin.compiler.execution.strategy=in-process'

# Instrumentation (needs device/emulator)
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w app.nicetime.android.debug.test/app.nicetime.android.MediaAcceptanceInstrumentation
```

### ⚠️ Local environment status (updated 2026-10-11)
- **Toolchain now installed** (portable, per-user, no admin): `C:\Users\ThinkPad\Downloads\code\tooling\`
  - JDK 17 → `tooling\jdk17` (Temurin 17.0.20.1)
  - Android SDK → `tooling\sdk` (platform-tools, `platforms;android-36`, `build-tools;34.0.0`)
  - `NiceTime\local.properties` → `sdk.dir=C:/Users/ThinkPad/Downloads/code/tooling/sdk` (forward slashes on purpose)
- **Build requires JAVA_HOME**: `sdkmanager` and Gradle both need it. Prefix every build command with:
  ```powershell
  $env:JAVA_HOME='C:\Users\ThinkPad\Downloads\code\tooling\jdk17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
  ```
- Gradle **8.13** (not 8.10.2 as `BUILD_AND_TEST.md` says — doc drift). Wrapper downloads from
  `services.gradle.org` **times out** through this network; the distribution was pre-seeded into
  `C:\Users\ThinkPad\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13-bin.zip`.
  If that cache is wiped, download the zip with PowerShell (`Invoke-WebRequest`) rather than the wrapper.
- Installer script: `C:\Users\ThinkPad\Downloads\code\tooling\setup.ps1`
- CI remains the authoritative release build (`.github/workflows/android.yml`).

### PowerShell gotchas hit while setting this up
- `java -version` / `sdkmanager` write to **stderr**; with `$ErrorActionPreference='Stop'` PowerShell
  turns that into a terminating error. Capture with `try { ... 2>&1 | Out-String } catch {}` or
  `Start-Process -RedirectStandardError`.
- `-replace '\\','\\\\'` **doubles too much**: the pattern is a regex and the replacement is literal.
  Use `Set-Content` with forward slashes for paths — Java accepts them everywhere.
- **Don't `gradlew --stop` a build you still want.** It kills the in-flight build too.

### Networking
- A local HTTP proxy was detected at **`127.0.0.1:10809`** via `[System.Net.WebRequest]::DefaultWebProxy`,
  but it is **not reliably listening** (verified: port refuses connections).
- **Direct HTTPS works** to `dl.google.com`, `repo.maven.apache.org`, `repo1.maven.org`,
  `plugins.gradle.org`, `maven.google.com`. So the toolchain and Gradle run **without** a proxy.
- `C:\Users\ThinkPad\.gradle\gradle.properties` exists but contains **no** `systemProp.*.proxy*`
  entries, on purpose. A 14 MB `--debug` log confirmed the failure mode was
  `Connect to 127.0.0.1:10809 failed: Connection refused`, i.e. proxy settings would break builds.
- **Symptom to recognise:** dependency failures surface as
  `Cannot invoke "java.util.List.get(int)" because "path" is null` — a useless NPE from
  `ModuleVersionResolveException.getMessage()`. That is *always* a resolution failure, not a code
  bug. Diagnose with:
  ```powershell
  .\gradlew.bat :app:checkDebugAarMetadata --debug *> build-debug.log
  Select-String -Path build-debug.log -Pattern "Could not resolve|Caused by:"
  ```
- `JAVA_TOOL_OPTIONS` / `_JAVA_OPTIONS` / `HTTP_PROXY` are all unset in this environment.
- AGP auto-installed **build-tools 35.0.0** on first build, so `sdk\build-tools\35.0.0` now exists
  alongside the 34.0.0 we requested.

---

## 4. Runtime architecture

### 4.1 Playback pipeline (the critical path)

```
UI (MainActivity)
  └─ provider.resolve(releaseRef, ResolvePurpose.STREAM)  →  ResolvedStream
        { uri, mimeType, requestHeaders, allowedHosts, subtitles, sourceLabel }
  └─ PlaybackCommands.play(mediaController, stream, positionMs)     [NiceTimePlaybackService.kt:82]
        └─ sendCustomCommand(PLAY_RESOLVED, bundle{uri,mime,headers,hosts,mediaId,position})
              └─ NiceTimePlaybackService.onCustomCommand            [NiceTimePlaybackService.kt:36]
                    └─ ResolvedMediaSourceFactory.create(...)
                          └─ scopedMediaClient(uri, headers, allowedHosts)   ← OkHttp w/ host allow-list
                          └─ OkHttpDataSource → DefaultDataSource → DefaultMediaSourceFactory
                    └─ player.setMediaSource(source, position); prepare(); play()
```

**Two service-owned classes** (both in `core/media`):
- `NiceTimePlaybackService : MediaSessionService` — owns the single process-wide `ExoPlayer` + `MediaSession`.
  - `onConnect` **rejects** controllers whose `packageName != packageName` (same-package only).
  - Commands exposed: `DEFAULT_SESSION_COMMANDS` + `PLAY_RESOLVED` + `PLAY_OFFLINE`.
- `NiceTimeDownloadService : DownloadService` (id 3102) — declared in app manifest.

**UI side:** `MainActivity.kt:108` builds one `MediaController` via `MediaController.Builder(context, SessionToken(...))`.
`PlayerDialog` (`MainActivity.kt:556`) renders it with `androidx.media3.ui.PlayerView` inside a Compose `Dialog`.

**Demo titles bypass the scoped client** — `DemoDetailsScreen.onPlay` calls `player.setMediaItem(MediaItem.fromUri(...))` directly (default `DefaultHttpDataSource`), not the custom command.

### 4.2 Demo catalog (always works offline-ish; used for testing)

`MainActivity.kt:91-96` — 4 hardcoded titles, all **HTTPS HLS test streams**:
| id | title | stream |
|---|---|---|
| bbb | Big Buck Bunny | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` |
| sintel | Sintel | `https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8` |
| tears | Tears of Steel | `https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8` |
| cosmos | Cosmos Laundromat | `https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8` |

### 4.3 Downloads

`DownloadRuntime` (object, `core/media/DownloadRuntime.kt`):
- `SimpleCache` in `getDir("offline_media")` with `NoOpCacheEvictor` + per-item cache key `$id|$key` → **downloads are never evicted by streaming**.
- `enqueue()` uses `DownloadHelper.forMediaItem` to resolve the segment list, then `DownloadService.sendAddDownload`.
- Request context (headers/hosts) is stored **encrypted** via `DownloadSecrets` bound to `CredentialStore` in `NiceTimeApplication`.
- States: `Queued / Downloading / Paused / Waiting for network / Insufficient storage / Offline ready / Failed: refresh source / Deleting`.
- `offlineSource()` plays **only** completed downloads, no network fallback.

### 4.4 Providers (`core/data`)

| Provider | Type | Enabled by default | Notes |
|---|---|---|---|
| **MovieBox** | signed REST client (HMAC-MD5 + guest JWT) | ✅ `true` | hosts `api*.aoneroom.com`, `api.inmoviebox.com`; DASH/CloudFront signed URLs |
| **4KHDHub** | HTML scraper (`https://4khdhub.one/`) | ✅ `true` | **BROKEN — see §6** |
| **Stremio addon** | user-supplied HTTPS manifest | user installs | real URLs but **no headers** passed |
| Demo | hardcoded | always | only reliable playback today |

- `NativeProviders.movieBox` / `.fourKHDHub` are process-wide `by lazy` singletons.
- `ProviderFactory.create(SourceConfiguration)` builds addon providers from persisted sources.
- `ProviderRegistry` aggregates (`catalogAll()`, `searchResults(query): Flow`).
- All `resolve()` implementations are **cache-only**: they return `Failure("Release must be refreshed")` on a cache miss.

---

## 5. Key data model (core/model)

```kotlin
// Catalog.kt
MediaRef(providerInstanceId, providerMediaId, type: MediaType)   // MediaType = MOVIE | SERIES
EpisodeRef(media: MediaRef, providerEpisodeId, season: Int?, episode: Int?)
ReleaseRef(episode: EpisodeRef, providerReleaseId, label, resolutionHeight, audioLanguage, container, downloadable)
ResolvedStream(release: ReleaseRef, uri: String, mimeType: String? = null,
               requestHeaders: Map<String,String> = emptyMap(),
               allowedHosts: Set<String> = emptySet(),   // empty = origin host only
               expiresAtEpochMs: Long? = null,           // NEVER read/written anywhere (dead)
               subtitles: List<SubtitleTrack> = emptyList(),
               sourceLabel: String)
MediaSummary(ref: MediaRef, title, year?, posterUrl?, backdropUrl?, synopsis?)
ProviderResult<T> = Ok<T> | Failure(ProviderFailure)     // sealed; Timeout/NetworkUnavailable/RateLimited/AuthExpired/NotFound/Unsupported/ParseFailure/SourceUnavailable
ProviderCapabilities(catalog, search, pagination, streamResolution, downloadable, externalSubtitles, supportedTypes, liveTv)
```

`ContentProvider` interface: `instanceId`, `capabilities`, `catalog()`, `search()`, `details()`, `streams()`, `resolve()`, `subtitle()`.

**UI navigation identities:**
- `demo_detail/{titleId}` — keyed by demo id
- `provider_detail/{titleName}/{sourceName}` — ⚠️ keyed by **title string**, then re-searched (`MainActivity.kt:285`) and takes `items.firstOrNull()`. Violates the project's own `Identity.kt` rule ("Never key by title or URL").

---

## 6. Known defects — playback blockers

> IDs here are stable; reference them in commits. Verified by code reading on 2026-10-11.

### Blocking (nothing plays)

| ID | Defect | Location |
|---|---|---|
| **PB-01** | **4KHDHub `resolve()` can never succeed.** `registerResolved()` is defined but never called; `resolved` map is always empty → `Failure("Release must be refreshed")` on every Play. `resolveMirror()` (whole Hubcloud/Hubdrive/pixeldrain chain) is also dead code. | `FourKHDHubProvider.kt:84-92, 360` |
| **PB-02** | **4KHDHub releases filtered out of UI.** `streams()` fabricates a *new* `EpisodeRef` with `providerMediaId = "$key:${System.nanoTime()}"`, but UI filters `it.ref.episode == chosen` where `chosen` comes from `details()`. Never equal → always `"No supported releases"`. Also non-deterministic id. | `FourKHDHubProvider.kt:216-221` vs `MainActivity.kt:307` |
| **PB-03** | **4KHDHub series episodes have empty media id** → `providerUrl("")` fetches the site homepage instead of the series page → wrong HTML parsed. | `FourKHDHubProvider.kt:266, 102-108` |
| **PB-04** | **Host allow-list kills CDN redirects/segments.** `allowedHosts` is set to exactly **one** host (the manifest origin). `ScopedMediaClient` network interceptor throws `IOException` for any other host. OkHttp still follows same-scheme redirects → any 302 to a CDN, or a DASH `BaseURL`/HLS segment on another host, fails with a generic error. **Most likely cause of real MovieBox/addon playback failure.** | `MovieBoxProvider.kt:156`, `StremioAddonProvider.kt:89`, `ScopedMediaClient.kt:16-18` |
| **PB-05** | **`followSslRedirects(false)`** blocks HTTP↔HTTPS redirects entirely; the redirect body is then treated as media. | `ScopedMediaClient.kt:13` |
| **PB-06** | **Silent failure.** `require(origin.host in allowed)` and `ResolvedMediaSourceFactory.create` errors are swallowed as `SessionResult(ERROR_BAD_VALUE)` — no `onPlayerError` fires, so the UI shows **nothing at all** (no dialog, no diagnostics entry). | `NiceTimePlaybackService.kt:52` |
| **PB-07** | **4KHDHub `catalog()` returns `Unsupported`** with no capability gate in `ProviderRegistry.catalogPaged` → Home shows red error text `"4KHDHub: Unsupported: catalog"`. | `FourKHDHubProvider.kt:34`, `ProviderRegistry.kt:160`, `MainActivity.kt:418` |

### High risk / correctness

| ID | Defect | Location |
|---|---|---|
| PB-08 | **`resolve()` never re-resolves, never expires.** `expiresAtEpochMs` is never set or read. MovieBox CloudFront signed URLs go stale; after process death a cold resolve always fails. | `MovieBoxProvider.kt:144`, `Catalog.kt:75` |
| PB-09 | **Stremio streams carry no `requestHeaders`** and `behaviorHints` is never read → addons needing `Referer`/`Origin` 403. | `StremioAddonProvider.kt:86-91` |
| PB-10 | **Discovery keyed by title, not identity** → can open the wrong movie. | `MainActivity.kt:179,184,285-287` |
| PB-11 | Hardcoded `Referer: https://sportslive.wine` broadcast on every MovieBox stream. | `MovieBoxProvider.kt:155` |
| PB-12 | MovieBox aborts host failover on first 401/403 instead of trying the other 6 hosts. | `MovieBoxProvider.kt:211` |
| PB-13 | 4KHDHub `if (!response.isSuccessful) → null` → misreported as `NetworkUnavailable`, hiding 403/404/Cloudflare. | `FourKHDHubProvider.kt:97, 41` |
| PB-14 | 4KHDHub mirror list is written but never read; dead merge branch drops duplicate quality variants. | `FourKHDHubProvider.kt:210-215, 235, 241` |
| PB-15 | 4KHDHub regex `.*?</div>` truncates nested markup → mirrors parse as empty → release dropped. | `FourKHDHubProvider.kt:463-464` |
| PB-16 | MovieBox declares `externalSubtitles = true` but no provider ever populates `subtitles`. `catalog=false` though `catalog()` works. | `MovieBoxProvider.kt:73` |
| PB-17 | `ProviderRegistry.fetchNextPage` uses the **cursor as the search query** (`SearchRequest(query=cursor)`); for MovieBox cursor is a page number → would search for `"2"`. Currently unreachable. | `ProviderRegistry.kt:136` |
| PB-18 | `MovieBoxProvider.http` (OkHttpClient with `Proxy.NO_PROXY`) is unused; real calls use `HttpURLConnection` which honors system proxy. Unused imports. | `MovieBoxProvider.kt:66, 193` |

### Fix status (updated 2026-10-11)

**Verified:** `assembleDebug lintDebug testDebugUnitTest` → **BUILD SUCCESSFUL**.
**46 unit tests, 0 failures** (FourKHDHub 12, RegressionSuite 9, ProviderRegistry 7, MovieBox 5,
ScopedMediaClient 5, AddonClient 4, Diagnostics 2, StremioAddon 2). Lint clean. APK 15.5 MB.
Note `:app` has **no unit tests** (`:app:testDebugUnitTest NO-SOURCE`) — `:app` coverage lives only
in the instrumentation suite, which still needs a device.

**Not verified:** actual on-device playback. `android/evidence/media-device-test.txt` still holds the
only recorded run (`FAIL: ExoPlaybackException: Source error`) and all AT-01..AT-26 rows are "Not run".
Everything above is proven by unit tests + build only.

Fixed in this session:

| ID | What changed | File |
|---|---|---|
| **PB-04** | Scoped client now **follows** cross-host redirects/segments but strips credentials (cookie/auth/referer/origin) from any host outside `allowedHosts`. Off-scope hosts get only `User-Agent`. | `ScopedMediaClient.kt` |
| **PB-05** | `followRedirects(true)` + `followSslRedirects(true)`; HTTPS→HTTP downgrade still refused with a clear reason. | `ScopedMediaClient.kt` |
| **PB-06** | `PlaybackCommands.play/playOffline` are now `suspend` and return `null` (ok) or a user-facing reason. Service validates and refuses; UI shows it. | `NiceTimePlaybackService.kt`, `MainActivity.kt` |
| **PB-07** | `ProviderRegistry.catalogPaged` gates on `capabilities.catalog`; `Unsupported` is no longer rendered as a red error on Home. 4KHDHub now implements a real `catalog()`. | `ProviderRegistry.kt`, `FourKHDHubProvider.kt`, `MainActivity.kt` |
| **PB-01** | `resolve()` re-fetches the source page and resolves mirrors on demand — no cache dependency. Dead `registerResolved` removed. `resolveMirror` returns a URL, not a Pair. | `FourKHDHubProvider.kt` |
| **PB-02** | Releases carry the **caller's** `MediaRef` and use a **deterministic** id `"$mediaId#$normalizedFilename"` (was `"$key:${System.nanoTime()}"`). UI now matches on media + season/episode instead of `EpisodeRef` equality. | `FourKHDHubProvider.kt`, `MainActivity.kt` |
| **PB-03** | `parseSeasons(html, ref)` carries the real media id (was `MediaRef(instanceId, "", SERIES)`). | `FourKHDHubProvider.kt` |
| **PB-12** | MovieBox records 401/403 and **keeps trying the other hosts**; reports `AuthExpired` only if every host refused. | `MovieBoxProvider.kt` |
| **PB-13** | `fetchText` throws a typed `HttpStatusException`; `failureFor()` maps 401/403→AuthExpired, 404→NotFound, 429→RateLimited, 5xx→SourceUnavailable, DNS/IO→NetworkUnavailable. | `FourKHDHubProvider.kt` |
| **PB-14** | Duplicates collapse via a keyed map; mirrors are re-read from the page on demand (`mirrorUrls` is now a warm cache only). | `FourKHDHubProvider.kt` |
| **PB-15** | Item extraction slices between item **start tags** (`itemBlocks`) instead of non-greedy `.*?</div>`, so nested markup no longer truncates a block before its mirror links. Lookbehind keeps `episode-download-item` from also matching `download-item`. | `FourKHDHubProvider.kt` |
| **PB-16** | MovieBox no longer claims `externalSubtitles` (never implemented); correctly claims `catalog = true`. | `MovieBoxProvider.kt` |
| **PB-18** | Unused OkHttp imports removed. | `MovieBoxProvider.kt` |
| **PB-19** | **4KHDHub movies were unplayable.** Movie pages have no `episode-*` markup, so `parseSeasons` returned nothing, the details screen had **no selectable row**, `selectedEpisode` stayed null, and the release list never loaded. Now synthesises one entry for `MOVIE`. Series episodes also keep their parsed filename as the title instead of rendering blank. | `FourKHDHubProvider.kt`, `FourKHDHubProviderTest.kt` |
| **PB-20** | **Crash on Android 8-12.** `URLDecoder.decode(String, Charset)` is API 33+, but `minSdk` is 26 — opening any provider title threw `NoSuchMethodError` before playback ever started. Switched to the API-1 UTF-8 String overload the file already had in its `Uri` helper. | `MainActivity.kt:227` |

**Still open:**

| ID | Defect | Notes |
|---|---|---|
| PB-08 | `expiresAtEpochMs` is never written, so `isStale()` never triggers. | Only matters once a provider knows the link lifetime. MovieBox signs via CloudFront cookies instead. |
| PB-09 | Stremio addon streams still carry no `requestHeaders`. | Out of scope for this pass (user chose MovieBox + 4KHDHub). |
| PB-10 | Discovery still navigates by **title string** and re-searches. | Can open the wrong movie. Needs `MediaRef` threaded through navigation. |
| PB-11 | MovieBox still sends a hardcoded `Referer: https://sportslive.wine`. | Untested against live CDN; may need removal if the CDN validates referer. |
| PB-17 | `ProviderRegistry.fetchNextPage` still uses the cursor as the search query. | Currently unreachable from the UI. |
| — | `MovieBoxProvider.http` (OkHttp, `Proxy.NO_PROXY`) is still unused; transport is `HttpURLConnection`. | Harmless; a possible future cleanup. |

### Evidence status
- `android/evidence/media-device-test.txt` = **`FAIL: ExoPlaybackException: Source error`** (only evidence file; no PASS record anywhere).
- All 26 acceptance rows AT-01..AT-26 = **"Not run"**.
- `SOURCE_MATRIX.md`: **zero** real content sources have playback evidence.

---

## 7. Docs index — where to look

| Question | File |
|---|---|
| What must the app do? | `android/PRD.md` (FR-/NFR- IDs) |
| How should it be built? | `android/ARCHITECTURE.md` |
| What's the task status? | `android/TASKS.md` (T-0xx) — *boxes are stale, trust the status lines* |
| What's blocking release? | `android/PHASE_COMPLETION.md` |
| Which sources work? | `android/SOURCE_MATRIX.md` |
| How do I build/test? | `android/BUILD_AND_TEST.md` |
| Why was a decision made? | `android/README.md` § decision register |
| What's proven? | `android/evidence/` (currently only a FAIL) |

**Version catalog** (all deps): `settings.gradle.kts` lines 15–48.

---

## 8. Conventions & gotchas

- **All UI is in one file**: `app/src/main/kotlin/app/nicetime/android/MainActivity.kt` (583 lines). Screens: `HomeScreen`, `ExploreScreen`, `DownloadsScreen`, `SettingsScreen`, `DemoDetailsScreen`, `ProviderDetailsScreen`, `PlayerDialog`, `MiniPlayer`.- Compose previews at bottom (`MainActivity.kt:583`).
- `MainActivity.kt:578` defines a **`private object Uri`** (URLEncoder wrapper) — *not* `android.net.Uri`. Don't "fix" it as a missing import.
- `@UnstableApi` (Media3) is used broadly; opt-in via `@OptIn(UnstableApi::class)`.
- Cleartext HTTP is **blocked** (debug `debug_network_security.xml` explicitly `cleartextTrafficPermitted="false"`; no release config). All known endpoints are HTTPS.
- Media3 HLS/DASH modules are `implementation` deps of `:core:media` — present at runtime for the app (needed by `DefaultMediaSourceFactory` reflection).
- Tests: unit tests under each module's `src/test/kotlin`; instrumentation `app/src/androidTest/.../MediaAcceptanceInstrumentation.kt` (uses MockWebServer + `src/androidTest/assets/sample.mp4`).
- `PHASE_COMPLETION.md` §3 is the project's "definition of done" — respect it when claiming completion.

### 8.1 Media3 / coroutines compile traps (all hit and fixed on 2026-10-11)

| Trap | Wrong | Right |
|---|---|---|
| `resume` resolves to an **internal** coroutines symbol | `import kotlinx.coroutines.resume` → `Cannot access 'DispatchedTask<T>.resume'... it is internal in file` | `import kotlin.coroutines.resume` |
| `CancellableContinuation.resume` in coroutines 1.8.1 | `continuation.resume(value)` → `No value passed for parameter 'onCancellation'` | Same fix — use the `kotlin.coroutines.resume` extension, which takes only the value |
| `SessionResult` has **no `(Int, Bundle)` ctor** | `SessionResult(code, bundle)` → `Cannot resolve constructor` | `SessionResult(SessionError.ERROR_BAD_VALUE)`; there is no per-result extras channel, so validate client-side instead |
| Accidentally nesting a `ListenableFuture` | `return try { Futures.immediateFuture(x) } catch { Futures.immediateFuture(badValue()) }` where `badValue()` already returns a future → `Return type mismatch ... actual ListenableFuture<out Any!>!` | `catch { badValue() }` — don't re-wrap |
| `@UnstableApi` opt-in contagion | Marking a helper `@UnstableApi` when it uses no Media3 API forces every caller to opt in | Only annotate declarations that actually touch Media3 (`PlaybackError` does not) |

`SessionResult` members that *do* exist: `RESULT_SUCCESS`, `resultCode`, and
`SessionError.ERROR_{UNKNOWN,INVALID_STATE,BAD_VALUE,PERMISSION_DENIED,SESSION_DISCONNECTED,NOT_SUPPORTED}`.

`MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(...)` sets
session commands only; player-level commands come from the player, so `setMediaItem`/`prepare`/
`play` over a `MediaController` work without extra configuration.

### 8.2 Lint is part of the build, and it catches real crashes

`lintDebug` **aborts the build** on errors and is the only thing that catches API-level mistakes.
Two `NewApi` errors were sitting in the tree (the docs claimed everything was green, which is how
you can tell `lintDebug` was never actually run):

- `java.net.URLDecoder.decode(String, Charset)` — **API 33**; the app's `minSdk` is **26**.
  Lint message: *"Call requires API level 33, or core library desugaring (current min is 26)"*.
  Rule of thumb: prefer the `(String, String)`/`(String, String, String)` overloads — they are
  API 1 and safe everywhere.
- Lint also scans `local.properties`. The `PropertyEscape` rule wants `C\:/Users/...`, i.e. the
  drive colon escaped. Not committed (`local.properties` is gitignored) but it breaks `lintDebug`.

**Always run `assembleDebug lintDebug testDebugUnitTest` together** — a green `assembleDebug` alone
does not mean the APK will run on a minSdk-26 device.

---

## 9. Decisions recorded from the user (2026-10-11)

1. **Sources in scope:** MovieBox **and** 4KHDHub (the two native providers). Stremio addons and the
   demo catalog are not the priority.
2. **Observed symptom:** the player opened and showed **"Playback failed. Retry or choose another
   source."** → that is the host-allow-list / redirect refusal (PB-04/PB-05), not a codec problem.
3. **Verification:** install a local toolchain (done — see §3) so changes are compiled and unit-tested
   before being handed back.
4. **"Make it dynamic" means:** *show only content that can actually be played*. Do not list releases,
   titles or sources that are known to fail — filter them out and offer a real reason when there is
   nothing playable.

---

## 10. Change log

Append one line per session. Format: `YYYY-MM-DD — what changed — sections updated`.

- 2026-10-11 — Initial knowledge base created from full code + docs audit. Found 18 playback defects (PB-01..PB-18); noted missing local JDK/SDK. — §§1-9
- 2026-10-11 — Installed portable JDK 17 + Android SDK (platform 36) per user choice; documented PowerShell/Gradle-wrapper gotchas. — §3
- 2026-10-11 — Fixed the "Playback failed" root cause: scoped HTTP client now follows cross-host CDN/segment redirects while stripping credentials; HTTPS→HTTP downgrade still refused. Updated `ScopedMediaClientTest` to assert the new (still credential-safe) contract. — §6, §4.1
- 2026-10-11 — Made start failures visible: `PlaybackCommands.play/playOffline` became suspend + reason-returning; service validates; UI shows the reason instead of a silent no-op. — §4.1
- 2026-10-11 — Rewrote the 4KHDHub path: real `catalog()`, deterministic release ids, caller identity preserved, dynamic re-resolution on Play, typed HTTP error mapping, non-truncating item parsing. — §4.4
- 2026-10-11 — MovieBox: host failover no longer aborts on the first 401/403; capability flags now tell the truth. — §4.4
- 2026-10-11 — UI: releases matched by media + season/episode (not full EpisodeRef equality); `Unsupported` no longer shown as a red error on Home. — §6
- 2026-10-11 — Found + fixed two more defects: 4KHDHub movies had no selectable row (unplayable), and an API-33-only `URLDecoder` overload crashed on minSdk-26 devices. — §6, §8.2
- 2026-10-11 — Verified green: `assembleDebug lintDebug testDebugUnitTest`, 46 tests / 0 failures, lint clean, 15.5 MB APK. Recorded that playback itself is still unverified on device. — §3, §6
