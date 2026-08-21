const base = {
  fill: "none",
  strokeWidth: 1.7,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  viewBox: "0 0 24 24",
};

export function FeatureIcon({ name }) {
  const paths = {
    sparkles: (
      <>
        <path d="M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9z" />
        <path d="M18 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" />
      </>
    ),
    "wifi-off": (
      <>
        <path d="M3 3l18 18" />
        <path d="M9.5 16.5a4 4 0 015 0" />
        <path d="M6.5 13a8.5 8.5 0 013.2-2" />
        <path d="M17.5 13a8.5 8.5 0 00-3.6-2.1" />
        <path d="M3.5 9.5a13 13 0 014-2.6" />
        <path d="M20.5 9.5a13 13 0 00-9-3.4" />
      </>
    ),
    library: (
      <>
        <path d="M4 4v16" />
        <path d="M8.5 4v16" />
        <rect x="12" y="4" width="7.5" height="16" rx="1.5" />
      </>
    ),
    play: (
      <>
        <circle cx="12" cy="12" r="8.5" />
        <path d="M10.4 9.2l4.6 2.8-4.6 2.8z" />
      </>
    ),
    list: (
      <>
        <path d="M4 7h11M4 12h11M4 17h7" />
        <path d="M19 6.5v8.2" />
        <circle cx="17.4" cy="16" r="1.6" />
      </>
    ),
    chart: (
      <>
        <path d="M4 20V10" />
        <path d="M10 20V4" />
        <path d="M16 20v-7" />
        <path d="M22 20H2" />
      </>
    ),
  };

  return (
    <svg {...base} stroke="currentColor" aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

export function DownloadIcon() {
  return (
    <svg {...base} stroke="currentColor" aria-hidden="true">
      <path d="M12 3.5v11" />
      <path d="M7.5 10.5L12 15l4.5-4.5" />
      <path d="M4.5 19.5h15" />
    </svg>
  );
}

export function GithubIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2a10 10 0 00-3.16 19.49c.5.09.68-.22.68-.48l-.01-1.7c-2.78.6-3.37-1.34-3.37-1.34-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.36 1.09 2.94.83.09-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02a9.5 9.5 0 015 0c1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.85l-.01 2.75c0 .27.18.58.69.48A10 10 0 0012 2z" />
    </svg>
  );
}
