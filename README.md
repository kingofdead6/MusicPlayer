# MusicPlayer

A local, offline-first Android music player with one extra trick: describe the playlist you
want in plain language and an LLM assembles it **from the songs already on your phone**.

Personal-use app. One user, one device, no account, no backend.

---

## The one guarantee

The model never names a song. It receives a numbered digest of your library:

```
0. Artist — Title (3:42)
1. Artist — Title (4:15)
```

…and answers with index numbers only. `PlaylistResponseParser.sanitize` drops every index
outside the valid range and every repeat before a `Track` is ever looked up, so there is no
path by which a title the model invented becomes a playlist entry.

Playback is entirely offline. Only playlist *generation* touches the network; if that call
fails you get a readable error and the rest of the app is untouched.

---

## Setup

1. Copy the example config and fill it in:

   ```bash
   cp local.properties.example local.properties
   ```

   ```properties
   sdk.dir=/path/to/Android/sdk
   LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
   LLM_API_KEY=...
   LLM_MODEL=gemini-2.5-flash
   ```

   `local.properties` is git-ignored. The three `LLM_*` keys reach the app through
   `BuildConfig` and are read in `LlmConfig.fromBuildConfig()`. The client appends
   `/chat/completions` to the base URL itself, so set the base URL without that suffix.
   Any OpenAI-compatible provider works; the app ships pointed at Google Gemini, whose
   key comes from https://aistudio.google.com/apikey

   Leave the `LLM_*` keys out and everything except the AI tab still works; that tab says so.

2. Build and install:

   ```bash
   ./gradlew :app:installDebug          # build + install on the connected device
   ./gradlew :app:assembleDebug         # just build the APK
   ./gradlew :app:testDebugUnitTest     # run the response-parser tests
   adb shell am start -n dev.nk.musicplayer/.MainActivity
   adb logcat -s LibraryRepository AiPlaylistGenerator LlmClient PlaybackService
   ```

Requirements: JDK 17+, Android SDK with API 35 installed. Gradle comes from the wrapper.

---

## What to test on device, phase by phase

Each phase is a separate commit, so you can `git checkout <commit>` and test that phase alone.

### Phase 1 — Library (`Phase 1: library scan and browse`)
- Deny the permission, then grant it; deny it twice and check you get the Settings path.
- Every music file on the device shows under Songs; ringtones and clips under 30 s do not.
- Search finds a title, an artist and an album — try an accented French title and an Arabic one.
- Pull down to rescan twice: the track count must not change and no row should duplicate.
- Artists and Albums tabs list and open correctly.

### Phase 2 — Playback (`Phase 2: background playback`)
- Play a track, lock the screen: audio keeps going, notification controls work.
- Swipe the app away while playing: it keeps playing.
- Call yourself: playback pauses and resumes afterwards.
- Yank the headphones: playback pauses.
- Seek, shuffle, repeat off/all/one.
- Long-press a queue row and drag it; remove a row.
- Kill and relaunch the app: the queue and position come back, paused.

### Phase 3 — Playlists (`Phase 3: manual playlists and .m3u8 export`)
- Create, rename, delete a playlist.
- Add tracks via the ⋮ on any song; drag to reorder; restart the app and confirm the order stuck.
- Export, then `adb pull /sdcard/Music/Playlists/<name>.m3u8` and open it in VLC.

### Phase 4 — AI playlists (`Phase 4: AI playlist generation`)
- Type *"traveling playlist, 3 hours, calm"*, pick the 3h chip, Generate.
- Check the result is real files from your phone, sensibly ordered, roughly 3 hours.
- Shuffle & play it, Regenerate it, then Save and confirm it appears under Playlists with the
  prompt shown underneath.
- Turn off wifi and mobile data and Generate: you should get a readable error, no crash, and
  playback should keep working.
- `adb logcat -s AiPlaylistGenerator` prints whether the single-pass or chunked path ran.

### Phase 5 — Stats (`Phase 5: play logging and stats`)
- Listen to a few tracks, skip a few, then open Stats.
- Total time, top artists, top tracks and the hour-of-day chart should match what you just did.
- Play a saved AI playlist through and watch its completion percentage move.

---

## Shape of the code

Single `:app` module, manual wiring in `AppContainer`, no DI framework.

```
data/db          Room entities, DAOs, aggregate projections
data/library     MediaStore scan + incremental rescan, paged queries
data/playlist    playlist CRUD, .m3u8 export
data/llm         digest building, chunking, HTTP client, response parser
data/stats       aggregate queries behind time windows
playback         MediaSessionService, MediaController wrapper, queue persistence, play logging
ui/*             Compose screens; one ViewModel where there is real state, plain flows elsewhere
```

### Notes on decisions that aren't obvious

- **`Track` carries three columns beyond the spec.** `dateModified` backs the incremental
  rescan, `isMissing` lets a deleted file be marked instead of deleted (keeping play history
  valid), and `searchText` is a lowercased, diacritic-stripped blob so `LIKE` search copes
  with French accents.
- **MediaItems lose their URI crossing the controller/session boundary.** The real URI travels
  in `requestMetadata` and `PlaybackService.onAddMediaItems` puts it back.
- **Listened time comes from the player's position when a track is left**, which media3 hands
  over in `onPositionDiscontinuity`. Exact for ends and skips; seeking forward then leaving
  over-counts.
- **Album art uses the deprecated `content://media/external/audio/albumart` provider**, with
  a note-icon fallback when it fails, rather than shipping an embedded-artwork decoder.
- **`WRITE_EXTERNAL_STORAGE` is declared with `maxSdkVersion="28"`**, needed only because
  API 26–28 has no `RELATIVE_PATH` for the playlist export. API 29+ needs no runtime grant.
- **Queues are capped at 1000 items** (500 for shuffle-all); a 10k-track ExoPlayer timeline
  buys nothing.

Out of scope by design: streaming, lyrics, equalizer, sleep timer, widgets, tag editing,
cloud sync, sharing, onboarding, analytics, and tests beyond the LLM response parser.
