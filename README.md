<p align="center">
  <img src="docs/banner.png" alt="Captain qBit" width="100%" />
</p>

<p align="center">
  <a href="https://f-droid.org/packages/dev.acamol.qbit/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
         alt="Get it on F-Droid" height="80">
  </a>
</p>

A modern Android app for managing a [qBittorrent](https://www.qbittorrent.org/) instance remotely
over its Web API — control your self-hosted server from your phone. Written in Kotlin.

## Features
- Free, open source, and respects your privacy — no ads, no tracking, no account
- Live torrent list with real-time speeds, progress, and ETA — sort, search, and filter by
  status, category, tag, or tracker
- Add via magnet links or `.torrent` files — straight from your browser or file manager — with
  per-file selection before you commit
- Full remote control: pause, resume, delete, recheck, and set queue priority or share limits,
  right from your phone
- Detailed per-torrent view with a browsable file tree, trackers, and live peer info
- Manage multiple servers, with encrypted backup/restore of your whole setup
- Light, Dark, or System theme, plus optional Material You dynamic colours
- Smart notifications that jump straight to a finished download, with battery-friendly refresh
  rates you control
- Browse RSS feeds and articles, with auto-download rules and notifications for new matches
- Works with qBittorrent 4.x and 5.x over HTTP or HTTPS (including self-signed certs), with
  optional HTTP Basic Auth for servers behind a reverse proxy

See the [F-Droid listing](https://f-droid.org/packages/dev.acamol.qbit/) for the full feature list.

> **Self-signed HTTPS:** certificates are validated properly (no blanket "trust everything"
> option). To use a self-signed cert, install it on your device via **Settings → Security →
> Install a certificate** — the app then trusts it with full validation. Plain HTTP on a LAN and
> normally-signed HTTPS work without any setup.

## Roadmap
- **Alternate speed-limit scheduler** — choose the time window and days when the alternate limits automatically take over

## Support

Captain qBit is free and open source. If you find it useful and want to support its
development, you can leave a tip on Ko-fi — entirely optional, and always appreciated.

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/acamol)

## Translations

Captain qBit is translated via [Weblate](https://hosted.weblate.org/engage/captain-qbit/).
If you'd like to help translate the app into your language, contributions are welcome there.

[![Translation status](https://hosted.weblate.org/widget/captain-qbit/svg-badge.svg)](https://hosted.weblate.org/engage/captain-qbit/)

## Credits

Captain qBit began as a fork of [Yash-Garg/qBittorrent-Manager](https://github.com/Yash-Garg/qBittorrent-Manager)
and builds on that foundation. Thanks to the original author and contributors for their work.

## License

Licensed under the [GNU General Public License v3.0](LICENSE.txt) — the same license as the
upstream project, so this app and any derivatives stay GPL-3.0.

- Original work © [Yash Garg](https://github.com/Yash-Garg) and contributors
- Modifications © 2026 Aviad Gafni
