// Single place to edit the app's public details.
export const APP = {
  name: "MusicPlayer",
  tagline: "Your library. Your phone. Your playlists.",
  repo: "https://github.com/kingofdead6/MusicPlayer",
  // Point this at a published release asset once you cut one.
  apk: "https://music-birds-app.vercel.app/MusicBirds.apk",
  version: "1.0",
  minAndroid: "8.0 (API 26)",
  size: "~22 MB",
};

export const FEATURES = [
  {
    icon: "sparkles",
    title: "Playlists from plain language",
    body: "Ask for a three-hour calm playlist for travelling and get one built from the songs already on your phone — never a title the model invented.",
  },
  {
    icon: "wifi-off",
    title: "Offline-first playback",
    body: "Playback never touches the network. Only playlist generation makes a request, and if it fails the rest of the app carries on untouched.",
  },
  {
    icon: "library",
    title: "Your whole library, indexed",
    body: "Scans every audio file on the device into Songs, Artists and Albums. Search copes with accents and non-Latin scripts, and rescans stay incremental.",
  },
  {
    icon: "play",
    title: "Proper background audio",
    body: "A media session that survives a locked screen or a swiped-away app, with notification controls, and pauses for calls and unplugged headphones.",
  },
  {
    icon: "list",
    title: "Playlists you control",
    body: "Create, rename, reorder by dragging, and export any playlist to a standard .m3u8 file that opens in VLC or anything else.",
  },
  {
    icon: "chart",
    title: "Listening stats",
    body: "Total listening time, top artists and tracks, and an hour-of-day chart, all logged on-device from what you actually play.",
  },
];

export const STEPS = [
  "Download the APK to your Android phone.",
  "Open it — Android will ask you to allow installs from this source. Allow it, then confirm.",
  "Grant the music permission on first launch so the library can be scanned.",
  "Optional: add your own LLM API key at build time to enable the AI tab. Everything else works without it.",
];
