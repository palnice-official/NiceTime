# Build and acceptance

The Android project is at the repository root. Application ID is `app.nicetime.android`; debug installs use `app.nicetime.android.debug`. No Rust build is changed.

## Local build

Use JDK 17, Gradle wrapper 8.10.2, AGP 8.6.1, Kotlin 2.0.20, Media3 1.4.1, Android platform 36 and build tools 34.0.0 (36.0.0 is also installed locally for artifact verification). Minimum API is 26. AGP's compile-SDK warning is suppressed by the existing configuration; this is a build-tested matrix, not a claim that AGP 8.6 officially supports SDK 36.

Set `JAVA_HOME` and `ANDROID_HOME`, or put the SDK location in untracked `local.properties`.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest :core:model:test lintDebug '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w app.nicetime.android.debug.test/app.nicetime.android.MediaAcceptanceInstrumentation
```

The device test downloads a synthetic three-second H.264/AAC clip from a local authenticated server, pauses/resumes through the download service, closes the server, renders a frame through the cache-only source and removes its own randomly identified download. It restores the previous download network preference. It does not clear existing app data or delete user downloads. Server shutdown demonstrates that offline playback does not need its origin; it does not substitute for reboot or airplane-mode acceptance.

The fixture is generated without third-party media:

```powershell
ffmpeg -f lavfi -i 'testsrc2=size=320x180:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac -movflags +faststart app/src/androidTest/assets/sample.mp4
```

Only debug builds permit HTTP to localhost/127.0.0.1. Release defaults remain HTTPS. Unit fixtures cover redirect rejection before credentials reach another host, same-host redirects, independent cookies, collision-resistant download IDs, immediate partial results, stale-query suppression and request limits.

CI configuration is in `.github/workflows/android.yml`; a local run does not prove that hosted CI has passed.

## Personal APK and recovery

The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. The personal release output is `app/build/outputs/apk/release/app-release.apk` after running `android/scripts/Build-Release.ps1`. A new RSA 3072 signing key was created at `C:\Users\Rama-office\.nicetime\signing\nicetime-release.p12`; its password is Windows-DPAPI protected in `password.clixml` beside it. Passwords are passed through process environment variables and never printed or committed. Release uses R8 shrinking and has no debug localhost exception.

Back up the keystore and its password independently using a secure password manager/offline destination. The DPAPI password file is bound to this Windows user/machine and is not a portable password backup. Do not regenerate the key for future updates. Key generation alone does not complete release acceptance.

Install updates over the existing package with the same signing key. Android may refuse lower version codes; older database schemas may not understand newer data. Prefer a forward-fix build. Uninstalling loses private history, preferences and offline downloads.

For a failed or expired download, remove that entry and resolve/download it again. The implementation intentionally refuses replacing its URL in place without resource identity proof. A missing required offline asset produces a playback failure, with no network fallback. Legacy download entries created by the previous cache-key scheme may need to be downloaded again; they are not silently deleted.

Remaining acceptance includes minimum-API coverage, process/reboot/force-stop recovery, adaptive track/subtitle coverage, source expiry, live independent providers, accessibility, sustained performance and release signing. See `TASKS.md` and the dated evidence ledger.
