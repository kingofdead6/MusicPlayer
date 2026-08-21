# MusicPlayer — website

A one-page React site introducing the MusicPlayer Android app, with a download
link for the APK. Built with Vite.

## Run it

```bash
npm install
npm run dev      # dev server
npm run build    # production build into dist/
npm run preview  # serve the production build locally
```

## Editing the content

Everything you are likely to change lives in [`src/content.js`](src/content.js):
app name, version, minimum Android version, APK size, the GitHub repo URL, the
feature cards, and the install steps.

**The download link.** `APP.apk` currently points at the repository's
`releases/latest` page, which works even before a release exists but sends the
visitor to a page rather than straight to a file. Once you publish a GitHub
release with the APK attached, swap it for the asset's direct URL so the button
downloads immediately:

```js
apk: "https://github.com/kingofdead6/MusicPlayer/releases/download/v1.0/app-release.apk",
```

`APP.size` is a placeholder — set it to the real APK size when you publish.

## Files

```
index.html        page shell, title and meta description
src/main.jsx      React entry point
src/App.jsx       the whole page: nav, hero, features, how-it-works, download, footer
src/content.js    app details and copy
src/Icons.jsx     inline SVG icons
src/index.css     all styling (dark theme, responsive)
public/icon.png   the app icon, copied from ../MusicPlayer/assets/
```

## Deploying

`npm run build` emits a static `dist/` that any static host will serve —
GitHub Pages, Netlify, Vercel, Cloudflare Pages. If you host it at a
sub-path (e.g. `user.github.io/MusicPlayer/`), set `base` in
[`vite.config.js`](vite.config.js) to that path.
