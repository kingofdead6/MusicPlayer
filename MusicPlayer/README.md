# MusicPlayer

A local, offline-first Android music player with one extra trick: describe the playlist you
want in plain language and an LLM assembles it **from the songs already on your phone**.

Before it curates, the app can *listen*. Each song is sampled, its vocals are transcribed by a
speech-to-text model, and what it is about — sentiment, mood, what it suits, energy, subject
matter — is worked out once and cached. Playlist requests are then answered from that, not
from filenames.

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

Playback is entirely offline. Only playlist *generation* and *song analysis* touch the
network; if either call fails you get a readable error and the rest of the app is untouched.

---

## How a song gets understood

The AI tab’s **Analyse my songs** button runs this over every song that has not been through it
yet, one at a time:

1. **Sample** — `AudioSampler` decodes three 30-second windows spread across the file
   (12%, 42%, 70% in, so an instrumental intro is not the whole sample), folds them to mono,
   resamples to 16 kHz and writes a WAV. Decoding through `MediaCodec` rather than re-wrapping
   with `MediaMuxer` means every codec the device can play works, MP3 included.
2. **Transcribe** — `SpeechToTextClient` posts those bytes to a hosted Whisper endpoint and
   gets back a rough, error-prone text of the vocals.
3. **Classify** — `SongAnalyzer` hands the transcript and the file's tags to the LLM, which
   returns sentiment, mood, category, genre, valence, energy, themes and one line of summary.
   `SongAnalysisParser` folds that free text onto a small fixed vocabulary — "melancholy",
   "somber" and "wistful" all become `melancholic` — clamps the numbers, and caps the themes,
   so the results are something the app can actually group by.

The verdict is cached in Room (`song_analysis`), so it costs two network calls once per song
and nothing thereafter. A run is resumable: stop it, lose the connection, or kill the app, and
the next run picks up the songs that are still untouched. Three failures in a row stop the run
and say why rather than burning through the library against a dead endpoint.

Songs the decoder cannot open, and instrumentals with nothing to transcribe, are still
classified — from title, artist and album alone — and the row records that, so the curator
knows to trust those tags less.

The transcript is a means, not a feature: a short excerpt is kept only so a re-analysis need
not pay for transcription twice, the classifier is asked to describe rather than quote, and
nothing in the UI renders it. Lyrics display stays out of scope.

Curation then gets a richer digest:

```
0. Artist — Title (3:42)
1. Artist — Title (4:15) | hopeful, positive | for: driving | energy .7 valence .8 | indie rock | about: road, freedom
```

Line 0 has not been analysed yet; the curator is told to judge it from artist and title and to
prefer an analysed song when the two are otherwise equal. The guarantee above is unchanged —
the reply is still nothing but index numbers.

---

## Setup

1. Copy the example config and set the SDK path:

   ```bash
   cp local.properties.example local.properties
   ```

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

2. Enter a Hugging Face API key in the app: **Settings → AI Playlists**. Create a free
   token at https://huggingface.co/settings/tokens. It is stored in the app's private
   SharedPreferences on the device, so no rebuild is needed to add or change it, and no key
   is ever committed.

   The same key is used for song analysis. Without a key everything except the AI tab still
   works; that tab says so.

   The optional `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` entries in `local.properties`
   still reach the app through `BuildConfig` and act as defaults — set `LLM_BASE_URL` and
   `LLM_MODEL` to point at any other OpenAI-compatible provider, otherwise the app uses the
   Hugging Face router. A key entered in Settings always wins over `LLM_API_KEY`. The client
   appends `/chat/completions` to the base URL itself, so set it without that suffix.

3. Build and install:

   ```bash
   ./gradlew :app:installDebug          # build + install on the connected device
   ./gradlew :app:assembleDebug         # just build the APK
   ./gradlew :app:testDebugUnitTest     # run the response-parser tests
   adb shell am start -n dev.nk.musicplayer/.MainActivity
   adb logcat -s LibraryRepository AiPlaylistGenerator LlmClient PlaybackService \
       AudioSampler SpeechToTextClient SongAnalyzer AnalysisRepository
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

### Phase 4b — Song analysis
- Open the AI tab: "0 of N songs analysed". Tap **Analyse my songs** and watch the counter and
  the current title move.
- Stop mid-run, then start again: it resumes on unanalysed songs and never redoes a finished
  one.
- Generate a playlist afterwards and check the preview rows carry mood/category chips, and
  that a request phrased as a feeling ("something for a bad day") picks differently than it
  did before analysis.
- Analyse an instrumental: it should still get a verdict, from its tags.
- Turn off the network mid-run: the run stops after three failures with a readable message,
  and the songs already analysed are kept.
- `adb logcat -s AudioSampler SongAnalyzer` shows the sample rate, frame count and each verdict.

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
data/analysis    audio sampling, speech-to-text, song classification, batch runner
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
- **Analysis is a manual, resumable batch, not part of the scan.** It costs two network calls
  per song against a free-tier endpoint, so the user decides when to spend them; firing songs
  in parallel would earn a rate limit rather than a speed-up.
- **Only three 30-second windows of each song are transcribed.** Whisper is charged by audio
  length and a chorus says more than a fourth verse; sampling across the track catches both a
  verse and a chorus for a tenth of a full transcription.
- **Model labels are folded onto a fixed vocabulary.** A classifier answers "melancholy" one
  call and "Melancholic vibes." the next; two spellings of one feeling make the digest worse,
  not better. Unrecognised labels survive as a plain word rather than being dropped.
- **`song_analysis` gets a real Room migration** while everything else falls back to a
  destructive rebuild: a rescan is seconds, re-analysing a library is not.
- **Queues are capped at 1000 items** (500 for shuffle-all); a 10k-track ExoPlayer timeline
  buys nothing.

Out of scope by design: streaming, lyrics display, equalizer, sleep timer, widgets, tag
editing, cloud sync, sharing, onboarding, analytics, and tests beyond the two response
parsers.
