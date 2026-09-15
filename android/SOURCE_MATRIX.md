# Candidate source matrix — 2026-09-15

Confirmed launch candidates remain MovieBox and 4KHDHub, with user-installed HTTP addons as the replacement path if either fails. Neither candidate is counted as a verified launch source yet. Their infrastructure/upstream independence is unknown; different adapter implementations alone do not prove it.

| Candidate | Implemented adapter behavior | Verified evidence | Still unknown / limitation |
| --- | --- | --- | --- |
| MovieBox | Search, metadata, episodes, signed MP4/DASH resolution, guest session, scoped request headers | Sanitized signing/session/mapping unit fixtures | Live source availability, regions, hardware playback, expiry, full offline assets, upstream overlap |
| 4KHDHub | Search, details, release/mirror resolution; no catalog | Sanitized parser and episode-filter fixtures | Live HTML compatibility, mirror reachability, hardware playback/offline support, upstream overlap |
| HTTP addon | Per-installation IDs, manifest validation, catalog/search/meta/streams, direct HTTP media | Controlled addon contract and failure fixtures | Specific launch endpoint needs selection and dated playback/download evidence; torrents and embeds unsupported |
| Synthetic local fixture | H.264/AAC progressive MP4, cookie authentication, HTTP range/ETag | Device acceptance runner in `app/src/androidTest` | Test media only; never counted as an independent launch source |

Fallback policy: configure a known working direct-media HTTP addon and run the same movie/series/play/download acceptance before replacing a failed candidate. A metadata-only addon does not qualify. No executable addon JavaScript, torrent engine, DRM integration or server proxy is introduced by this work.

No source passwords, tokens, guest sessions or signed media URLs should appear in this matrix or reports.
