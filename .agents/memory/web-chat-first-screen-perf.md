---
name: Web-chat first-screen perf measurement
description: How LAN-phone first-screen load cost is measured and where the baseline lives.
---

First-screen (connection overlay) download cost is measured by `web-chat/scripts/measure-first-screen.mjs` (npm: `measure`, `measure:baseline`, `measure:check`). It is a dependency-free static analyzer of `web-chat/dist`, NOT a headless browser — no puppeteer/playwright is installed.

**What counts as first-screen:** whatever Vite marks eager in `dist/index.html` — the entry `<script type=module>` plus every `<link rel=modulepreload>` chunk, plus the stylesheet. Everything else in `dist/assets/*.js` is treated as deferred (lazy). Markdown, AgentActivityPanel, CodexVersionManager are intentionally lazy (see ChatScreenContent.tsx + MarkdownRenderer.tsx) and must stay OFF the eager list, or the markdown vendor bundle (~43 kB gzip) re-enters the critical path.

**Rule:** the saved baseline (`web-chat/perf/first-screen-baseline.json` + `first-screen-report.md`) reflects a specific build. After an *intentional* first-screen change, rebuild then `npm run measure:baseline` to refresh it. `measure:check` fails (exit 1) if first-screen gzip JS grows >5% vs baseline.

**Why:** no browser automation is available here, so exact byte sizes (raw/gzip/brotli) + a transparent network-time estimate are the repeatable signal; the byte numbers are exact, the time figures are a network-only TTI lower bound (parse/exec excluded).
