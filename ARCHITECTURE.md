# BitChord — what to know, how it works, libraries & services

BitChord is an unofficial **YouTube Music Android client** (`com.music.bitchord`). It does **not** use an official YouTube API. Browse/search/library talk to YouTube’s **Innertube** (`youtubei/v1`); actual audio URLs are resolved with **NewPipeExtractor** + player-JS deobfuscation, then played by **Media3 / ExoPlayer**.

License: **GPLv3**. Automix code ported from [Orchard](https://github.com/SFG5453/Orchard) is **AGPLv3**. Not affiliated with Google, YouTube, Spotify, Tidal, Apple, Discord, etc.

---

## 1. What you need to know before changing code

### Stack

| Piece | Version / note |
|---|---|
| Language | Kotlin, JVM 17 |
| UI | Jetpack Compose + Material 3 |
| Android | `minSdk 26`, `targetSdk` / `compileSdk` 36 |
| AGP | 8.10.1, **R8 forced to 8.13.23** (see root `build.gradle.kts`) |
| Kotlin | 2.3.20 (Compose + serialization plugins) |
| Native | CMake 3.22.1, C++17 (`native/` + JNI in `app/src/main/cpp`) |
| Current app version | `1.5.2` (`versionCode` 14) |

### Product flavors

- **`prod`** — `com.music.bitchord` (shipped package)
- **`dev`** — `com.dev.bitchord` (“BitChord Dev”) so both can sit on one phone

Portrait only. Android Auto, media widgets, and `APP_MUSIC` intent filters are wired in the manifest.

### Build / secrets

| File | Purpose |
|---|---|
| `local.properties` | Android SDK path **and** `LASTFM_API_KEY` / `LASTFM_SECRET` (never committed) |
| `keystore.properties` + `*.jks` | Release signing. Missing → unsigned APK instead of a failed build |
| GitHub Actions | `.github/workflows/android.yml` — `assembleDevDebug` off main, signed `assembleProdRelease` on main |

Last.fm scrobbling is a no-op until those keys exist. Discord / Google cookies are stored at runtime, not at build time.

### Load-bearing build quirks (easy to break)

1. **R8 minify is off** on release. Stream resolution runs YouTube player JS through **Rhino**; NewPipe, Ktor, and kotlinx.serialization all use reflection. Shrinking without proven keep rules breaks playback at runtime.
2. **NewPipeExtractor is pinned to `v0.26.3`**, not newer. `v0.26.4` fails to parse the current player JS (`Could not parse deobfuscation function`).
3. **A patched `Utils.java` is vendored** at `app/src/main/java/org/schabi/newpipe/extractor/utils/Utils.java` (API 26-safe URL encode/decode). Gradle **strips** the jar’s copy before dexing. Removing the strip task breaks `prodRelease`.
4. **AGP 8.10.1’s bundled D8 miscompiles `NowPlayingScreen`** in debug. The root buildscript overrides R8/D8 to `8.13.23`. Do not drop that without verifying a debug player launch.
5. **One shared OkHttp client** (`data/Http.kt`) is used by Innertube, NewPipe, ExoPlayer, and caches. googlevideo binds stream URLs to the connection that minted them; a second HTTP stack can 403.

### What this clone does **not** include

Automix expects ONNX weights in `app/src/main/assets/`:

- `beat_this_int8.onnx` (Beat This! beat/downbeat)
- `vocals_umxhq_int8.onnx` (open-unmix vocals)

They are **not** in this git tree. Missing models log a warning and Automix degrades (tempo/key DSP still runs; beat-matched mixes will not). Playback still works.

---

## 2. How it works (runtime path)

```
UI (MainActivity + Compose screens)
        │  MediaController
        ▼
PlaybackService  (MediaLibraryService / ExoPlayer + MediaSession)
        │  queue item = bitchord://source?s=<config>&t=<track>  (or YT / local URI)
        ▼
ResolvingDataSource  — stream URL is resolved at play time (URLs expire in hours)
        │
        ▼
SourceResolver  — pick source, quality ceiling (Wi‑Fi vs mobile), fallback
        │
        ├── Addon HTTP  (/manifest.json, /search, /stream)  → FLAC / DASH / HLS
        ├── Module JS   (QuickJS plugins; legacy)
        ├── JioSaavn    (encrypted CDN URL → up to 320 kbps AAC)
        └── YouTube     Innertube player + StreamResolver + NewPipe n-param
                │
                ▼
        ExoPlayer  → notification / lockscreen / Auto / widgets
```

Startup (`BitChordApplication`): restore encrypted Google cookie → seed `Innertube` → `AppSettings`, `SourceRegistry`, caches, downloads, listening stats.

### Playback

`PlaybackService` is the real player. The UI never owns ExoPlayer; it attaches with `MediaController`.

- Queue items keep a **stable URI**, not a googlevideo URL. `ResolvingDataSource` resolves on the loader thread when the item opens.
- **Crossfade** is volume automation on one ExoPlayer (`CrossfadeController`), not two decoders.
- **Quality** is per network: Wi‑Fi ceiling vs mobile ceiling. Downloads use a separate `downloadQuality` so a data cap does not silently write a worse file.
- **Autoplay / radio** come from YouTube related tracks. Lossless addons can substitute the same recording via `TrackMatcher`.
- Local files and MediaStore downloads play as ordinary URIs.

### YouTube Music (Innertube)

`data/innertube/Innertube.kt` is a small youtubei client.

- **WEB_REMIX** against `music.youtube.com` for browse / search / library (honours the signed-in cookie).
- **Device clients** for `player` (stream URLs). `StreamResolver` walks a list (`ANDROID_MUSIC`, `TVHTML5`, …) because Google kills identities without notice.
- Auth is the **session cookie** from a WebView login, signed with **SAPISIDHASH**. No OAuth token is stored.
- **`visitorData` is mandatory.** Without it, `player` either returns `LOGIN_REQUIRED` or a URL that 403s after one byte. That is the usual “it loads then won’t play” bug.
- Media fetches must repeat the **same User-Agent / Origin** as the client that minted the URL.

`StreamResolver` then:

1. Asks `player` (cheap JSON), not the watch HTML page.
2. Runs YouTube’s player JS via NewPipe (`YoutubeJavaScriptPlayerManager` + Mozilla Rhino) to solve the `n` throttle parameter and signatures.
3. **Probes one byte** of the URL before handing it to ExoPlayer or caching it.

`YtMusicRepository` is the UI-facing wrapper: home, search, library, playlists, likes, radio.

### Sources (audio backends)

Tried in this order (`SourceKind.rank`):

1. **Addon** — user-pasted HTTP server. Eclipse Music protocol: `GET /manifest.json`, `/search`, `/stream/{id}`. No JS. This is how Hi-Res / Tidal / Qobuz-style lossless is added. Spec: https://eclipsemusic.app/docs
2. **Custom module / module** — legacy. Index JSON lists JS plugins; each exports `searchTracks()` / `getTrackStreamUrl()` and runs in a **QuickJS sandbox** (`quickjs-kt`) with a wired `fetch()`. Sources screen no longer creates these; old configs still work.
3. **JioSaavn** — built-in lossy fallback, up to 320 kbps AAC. Public `api.php`; encrypted media URLs decrypted locally.
4. **YouTube Music** — always present, cannot be deleted, only disabled. Only source for home feed, radio, related tracks.

`SourceRegistry` stores addon URLs in **EncryptedSharedPreferences**.

### Lyrics

`LyricsRepository` races enabled sources, then **takes answers in user order**. Word-synced wins over line-synced if that setting is on. Genius is last and **lazy** (plain HTML scrape).

### Canvas (motion artwork)

`CanvasRepository` asks, in order: **Apple Music** (HLS editorial video) → **Tidal** video cover → **community JSON index** → **Spotify Canvas** (needs the user’s Spotify cookie; heaviest, so last). Misses are cached. Local files skip the network.

### Automix (beta)

On-device DJ transitions, ported from Orchard:

1. Native DSP (`native/analyzer`) — tempo, key, energy, structure (no ML).
2. **Beat This!** ONNX — beats + downbeats (meter).
3. **open-unmix** ONNX — vocal mask so a mix does not pile two singers.

ONNX Runtime runs on the Kotlin side; C++ only builds spectrograms. Failures must not crash playback.

### Accounts & extras

| Feature | How |
|---|---|
| Google / YT Music login | WebView on accounts.google.com → cookie in `AuthStore` |
| Brand accounts | Innertube `pageId` / `dataSyncId` channel override |
| Discord RPC | Unofficial user gateway (vendored Kizzy). Bearer token from Discord WebView. Settings warn before login. |
| Last.fm / Libre.fm | Official API; keys from `local.properties` |
| ListenBrainz | `https://api.listenbrainz.org/1/submit-listens` + user token |
| In-app updates | GitHub Releases latest tag vs `versionName`; APK via FileProvider |
| Replay | Local listening stats (`ListeningStats`), not a cloud service |

---

## 3. Third-party libraries (Gradle)

From `app/build.gradle.kts`:

### UI

| Library | Why |
|---|---|
| Compose BOM 2024.12.01 + foundation **1.10.0** | UI. Foundation is pinned above the BOM for `OverscrollFactory` (`IosOverscroll`) |
| Material 3, icons-extended | Theming / icons |
| Navigation Compose 2.8.5 | Screens |
| Haze 1.3.1 | Telegram-style frosted bars |
| Coil 3.0.4 | Artwork |
| Palette-ktx | Dominant colors → mesh gradient |
| compose-richtext 0.20.0 | Markdown in the update dialog (must be Compose, not WebView, to sit under Haze) |

Vendored UI (source in-tree, not Gradle):

- `ui/components/backdrop/` — [Kyant0/backdrop](https://github.com/Kyant0/backdrop) (Apache 2.0)
- `ui/components/floatingtabbar/` — [compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar)
- `ui/utils/IosOverscroll.kt` — from [Convx](https://github.com/cosmictaserdev-creator/Convx)

### Playback & media

| Library | Why |
|---|---|
| Media3 ExoPlayer / session / okhttp **1.11.0** | Player, notification, Auto |
| media3-exoplayer-hls | Apple motion artwork |
| media3-exoplayer-dash | Addon/Tidal `.mpd` manifests (without this, XML is sniffed as audio and fails) |
| kotlinx-coroutines-guava | Media3 `ListenableFuture` bridges |

### YouTube / HTTP / JSON

| Library | Why |
|---|---|
| Ktor 3.0.3 (OkHttp + JSON + **WebSockets**) | Innertube + Discord gateway |
| kotlinx.serialization 1.7.3 | JSON |
| NewPipeExtractor **v0.26.3** (JitPack) | Signature + `n` deobfuscation |
| nanojson, jsoup, protobuf-javalite | NewPipe transitives (declared by hand because the extractor jar is re-packed) |
| Mozilla Rhino 1.8.1 | Run YouTube player JS |
| OkHttp (via Ktor / Coil / Media3) | Shared `Http.client` |

### Plugins, ML, auth

| Library | Why |
|---|---|
| quickjs-kt-android 1.0.5 | Sandbox for module JS plugins |
| onnxruntime-android 1.28.0 | Beat This! + vocal models (full android artifact, not `-mobile`) |
| security-crypto 1.1.0-alpha06 | Encrypted prefs for Google cookie, Discord token, addon URLs |

### Tests

JUnit 4, OkHttp MockWebServer, coroutines-test, AndroidX Espresso.

---

## 4. External services (network)

None of these are first-party BitChord servers except GitHub Releases.

### Catalogue & audio

| Service | Role |
|---|---|
| **YouTube / YouTube Music Innertube** | Home, search, library, likes, radio, player URLs |
| **googlevideo.com** | Actual audio bytes |
| **JioSaavn** `www.jiosaavn.com/api.php` | Lossy fallback streams |
| **User addon hosts** | Whatever URL the user pastes (often Tidal/Qobuz-style lossless, DASH/HLS) |
| **Module index + plugin JS** | Legacy; fetched and executed in QuickJS |

### Lyrics

| Source | Endpoint / method | Timing |
|---|---|---|
| LyricsPlus | volunteer mirrors (`lyricsplus.*`) | syllable |
| PaxSenix | `lyrics.paxsenix.org` (+ Apple catalog scrape) | word |
| BetterLyrics | `lyrics-api.boidu.dev` (Apple TTML) | word |
| SimpMusic | `api-lyrics.simpmusic.org` keyed on **video id** | word |
| KuGou | `lyrics.kugou.com` | line |
| LRCLIB | `lrclib.net/api` | line |
| Musixmatch | `apic.musixmatch.com` (web-client HMAC) | line |
| Genius | HTML scrape via Jsoup | none (plain text) |

Embedded LRC/TTML in downloaded files is read first when offline.

### Canvas / extras

| Service | Role |
|---|---|
| Apple Music catalog (`amp-api.music.apple.com`) | Motion artwork (token scraped from the public web player) |
| Tidal public search (`api.tidal.com`) | Square video covers |
| Community canvas JSON | `vivimusicanvas.mkmdevilmi.workers.dev/canvas.json` |
| Spotify Canvas | Optional; needs user session |
| Discord gateway | Rich Presence as the **user**, not a bot |
| Last.fm / Libre.fm | Scrobble |
| ListenBrainz | Scrobble |
| GitHub Releases API | Update check + APK download |
| Last.fm artist tags | Replay / artist facts |

---

## 5. Package map (where to look)

```
app/src/main/java/com/music/bitchord/
  MainActivity.kt              Compose shell, tabs, deep links, share
  BitChordApplication.kt       Process init
  auth/                        WebView login, encrypted sessions
  data/
    innertube/                 youtubei client + StreamResolver + parsers
    sources/                   Addon / module / JioSaavn / YouTube + SourceResolver
    lyrics/                    All lyric providers
    canvas/                    Motion artwork
    discord/                   RPC wrapper around Kizzy
    scrobbling/                Last.fm + ListenBrainz
    jiosaavn/                  JioSaavn HTTP + decrypt
    stats/                     Local listen history, Replay, backup
    YtMusicRepository.kt       UI API over Innertube
    Http.kt                    Shared OkHttp
  playback/                    PlaybackService, crossfade, cache, queue, Auto
    smart/                     Automix analysis (Orchard + ONNX)
  download/                    Offline DASH/HLS, taggers (FLAC/MP4/WebM)
  ui/                          Screens, player, Haze, widgets helpers
  widget/                      Home-screen 2×2 and 4×2

native/analyzer/               C++ DSP (tempo, mel, vocal STFT)
app/src/main/cpp/jni/          JNI glue
com/my/kizzy/                  Vendored Discord gateway client
org/schabi/newpipe/.../Utils   Patched NewPipe class
```

Tabs in `MainActivity`: Home, Explore, Search, Library, plus Now Playing, Settings, Sources, Discord, Replay, Local music.

---

## 6. Important rules when you change things

- **Do not bump NewPipeExtractor** without a live `player` JS parse test.
- **Do not enable R8 minify** without a device playback test (cipher + serialization).
- **Do not give ExoPlayer / Innertube separate OkHttp clients.**
- **Resolve streams lazily.** Caching googlevideo URLs in the queue will expire.
- **Probe URLs** before play; a 200 on `player` JSON is not a working stream.
- **Keep `visitorData` + cookie + client UA together.** Mixing them is 403 / throttle.
- Addon failures must fall through to JioSaavn then YouTube, not kill the track.
- Automix / lyrics / canvas failures must be silent; still art and audio always work.
- Source configs and auth belong in encrypted prefs, not `AppSettings`.
- `NowPlayingScreen` is huge; debug dexing is why R8 is overridden — be careful growing it further.

---

## 7. Local build

```bash
# Debug (dev flavor, side-by-side with prod)
./gradlew assembleDevDebug

# Unsigned prod if no keystore.properties
./gradlew assembleProdRelease
```

Needs JDK 17, Android SDK 36, CMake 3.22.1, NDK (for `bitchord_analysis`). Optional: `LASTFM_API_KEY` / `LASTFM_SECRET` in `local.properties`. Optional: drop the two `.onnx` files into `app/src/main/assets/` for Automix.
