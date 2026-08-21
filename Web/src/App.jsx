import { APP, FEATURES, STEPS } from "./content";
import { DownloadIcon, FeatureIcon, GithubIcon } from "./Icons";

function Nav() {
  return (
    <header className="nav">
      <div className="wrap nav-inner">
        <a className="brand" href="#top">
          <img src="/icon.png" alt="" />
          {APP.name}
        </a>
        <nav className="nav-links">
          <a href="#features">Features</a>
          <a href="#how">How it works</a>
          <a href="#download">Download</a>
          <a href={APP.repo} target="_blank" rel="noreferrer">
            GitHub
          </a>
        </nav>
        <a className="btn btn-primary" href="#download">
          <DownloadIcon />
          Get the APK
        </a>
      </div>
    </header>
  );
}

function PhoneMock() {
  const tracks = [
    ["Nuit Sans Fin", "Céline Arnaud · 4:12"],
    ["Long Road Home", "The Wandering · 3:48"],
    ["ليل هادئ", "Amine Haddad · 5:02"],
    ["Slow Tide", "Marisa Kell · 4:31"],
  ];

  return (
    <div className="phone" aria-hidden="true">
      <div className="phone-screen">
        <div className="screen-label">AI playlist</div>
        <div className="prompt-box">
          traveling playlist, calm, nothing too loud
        </div>
        <div className="chips">
          <span className="chip">1h</span>
          <span className="chip on">3h</span>
          <span className="chip">5h</span>
        </div>
        <div className="screen-label">42 tracks · 2h 58m</div>
        {tracks.map(([title, sub]) => (
          <div className="track" key={title}>
            <div className="art" />
            <div className="track-meta">
              <div className="track-title">{title}</div>
              <div className="track-sub">{sub}</div>
            </div>
          </div>
        ))}
        <div className="mini">
          <div className="mini-row">
            <div className="art" />
            <div className="track-meta">
              <div className="track-title">Nuit Sans Fin</div>
              <div className="track-sub">Céline Arnaud</div>
            </div>
          </div>
          <div className="bar">
            <span />
          </div>
        </div>
      </div>
    </div>
  );
}

function Hero() {
  return (
    <div className="hero" id="top">
      <div className="wrap hero-inner">
        <div>
          <span className="pill">
            <span className="dot" />
            Android · no account, no backend
          </span>
          <h1>
            Your library. Your phone.{" "}
            <span className="grad">Playlists in plain language.</span>
          </h1>
          <p className="lede">
            {APP.name} is an offline-first music player for the songs already on
            your device. Describe the playlist you want and it assembles one from
            your own files — nothing streamed, nothing invented.
          </p>
          <div className="cta-row">
            <a className="btn btn-primary" href={APP.apk}>
              <DownloadIcon />
              Download for Android
            </a>
            <a
              className="btn btn-ghost"
              href={APP.repo}
              target="_blank"
              rel="noreferrer"
            >
              <GithubIcon />
              View source
            </a>
          </div>
          <p className="cta-note">
            Version {APP.version} · Android {APP.minAndroid} or newer
          </p>
        </div>
        <PhoneMock />
      </div>
    </div>
  );
}

function Features() {
  return (
    <section id="features">
      <div className="wrap">
        <div className="section-head">
          <h2>Everything a local player should do</h2>
          <p>
            Built as a personal music player first, with one extra trick bolted
            on top rather than a streaming service wearing a player&apos;s
            clothes.
          </p>
        </div>
        <div className="grid">
          {FEATURES.map((f) => (
            <article className="card" key={f.title}>
              <div className="icon">
                <FeatureIcon name={f.icon} />
              </div>
              <h3>{f.title}</h3>
              <p>{f.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function HowItWorks() {
  return (
    <section id="how">
      <div className="wrap">
        <div className="callout">
          <div>
            <h2>The model never names a song</h2>
            <p>
              It only ever sees a numbered digest of your library and answers
              with index numbers. Every index outside the valid range, and every
              repeat, is dropped before a track is looked up — so there is no
              path by which an invented title reaches your playlist.
            </p>
            <p>
              Playback stays entirely offline. If the generation call fails you
              get a readable error and the rest of the app keeps working.
            </p>
          </div>
          <pre>
            <code>
              <span className="k">0.</span> Céline Arnaud — Nuit Sans Fin (4:12)
              {"\n"}
              <span className="k">1.</span> The Wandering — Long Road Home (3:48)
              {"\n"}
              <span className="k">2.</span> Marisa Kell — Slow Tide (4:31)
              {"\n\n"}
              → model replies: <span className="k">[2, 0, 1]</span>
            </code>
          </pre>
        </div>
      </div>
    </section>
  );
}

function Download() {
  return (
    <section className="download" id="download">
      <div className="wrap">
        <div className="section-head">
          <h2>Download for Android</h2>
          <p>
            Distributed as a direct APK — there is no Play Store listing. iOS is
            not supported.
          </p>
        </div>
        <div className="dl-card">
          <img src="/icon.png" alt={`${APP.name} app icon`} />
          <a className="btn btn-primary" href={APP.apk}>
            <DownloadIcon />
            Download APK
          </a>
          <div className="specs">
            <span>
              Version <b>{APP.version}</b>
            </span>
            <span>
              Requires <b>Android {APP.minAndroid}</b>
            </span>
            <span>
              Size <b>{APP.size}</b>
            </span>
          </div>
        </div>
        <ol className="steps">
          {STEPS.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer>
      <div className="wrap footer-inner">
        <span>
          {APP.name} — {APP.tagline}
        </span>
        <a href={APP.repo} target="_blank" rel="noreferrer">
          Source on GitHub
        </a>
      </div>
    </footer>
  );
}

export default function App() {
  return (
    <>
      <Nav />
      <main>
        <Hero />
        <Features />
        <HowItWorks />
        <Download />
      </main>
      <Footer />
    </>
  );
}
