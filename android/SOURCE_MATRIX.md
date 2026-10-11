# Candidate source matrix — 2026-09-15

Confirmed launch candidates remain MovieBox and 4KHDHub, with user-installed HTTP addons as the replacement path if either fails. 4KHDHub now has one dated emulator search-to-playback check, but physical-phone playback, offline media and sustained reliability are still unverified; it is not yet counted as a verified launch source. Their infrastructure/upstream independence is unknown; different adapter implementations alone do not prove it.

| Candidate | Implemented adapter behavior | Verified evidence | Still unknown / limitation |
| --- | --- | --- | --- |
| MovieBox | Search, metadata, episodes, signed MP4/DASH resolution, guest session, scoped request headers | Sanitized signing/session/mapping unit fixtures | Live source availability, regions, hardware playback, expiry, full offline assets, upstream overlap |
| 4KHDHub | Home catalog, search, details, release/mirror resolution | 11 parser/provider unit tests; 2026-09-15 live emulator search, details, 1080p H.264 mirror resolution and rendered video frame | Physical-phone playback, offline/large-file completion, signed-link expiry, regions, other codecs/mirrors, upstream overlap |
| HTTP addon | Per-installation IDs, manifest validation, catalog/search/meta/streams, direct HTTP media | Controlled addon contract and failure fixtures | Specific launch endpoint needs selection and dated playback/download evidence; torrents and embeds unsupported |
| Synthetic local fixture | H.264/AAC progressive MP4, cookie authentication, HTTP range/ETag | Device acceptance runner in `app/src/androidTest` | Test media only; never counted as an independent launch source |

Fallback policy: configure a known working direct-media HTTP addon and run the same movie/series/play/download acceptance before replacing a failed candidate. A metadata-only addon does not qualify. No executable addon JavaScript, torrent engine, DRM integration or server proxy is introduced by this work.

No source passwords, tokens, guest sessions or signed media URLs should appear in this matrix or reports.
