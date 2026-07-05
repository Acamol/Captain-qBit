<p align="center">
  <img src="docs/banner.png" alt="Captain qBit" width="100%" />
</p>

A modern Android app for managing a [qBittorrent](https://www.qbittorrent.org/) instance remotely
over its Web API — control your self-hosted server from your phone. Written in Kotlin.

## Features

### Torrent list
- Live global and per-torrent download/upload speeds, progress, and ETA
- Sort by name, size, progress, speed, ETA, ratio, date added, and more — tap again to reverse
- Search torrents by name from a collapsible search bar
- Filter by status, category, tracker, or tags, with active filters shown as dismissible chips
- Colour-coded status matching the qBittorrent Web UI (downloading, seeding, paused, errored…)

### Adding torrents
- Add via magnet links or `.torrent` files, or open them from other apps
- Choose a category (pick an existing one or create a new one on the spot), set a save path,
  start paused, or enable Automatic Torrent Management
- Detects duplicates before adding, so you don't re-add a torrent you already have

### Managing torrents
- Pause/resume, delete (optionally with data), force recheck, reannounce, and rename
- Set or create categories, manage tags, toggle Automatic Torrent Management, or change the save path
- Detail view with Info, Files, Trackers, and Peers tabs — browse the file tree and copy full paths
- Surfaces the actual failure reason when a torrent errors, instead of a bare "error"

### Connection & reliability
- Works with qBittorrent 4.x and 5.x
- Optional HTTP Basic Auth for servers behind a reverse proxy
- Edit a saved server without re-adding it
- Short, readable messages for network errors
- A persistent status notification showing connection state and current speeds

> **Self-signed HTTPS:** certificates are validated properly (no blanket "trust everything"
> option). To use a self-signed cert, install it on your device via **Settings → Security →
> Install a certificate** — the app then trusts it with full validation. Plain HTTP on a LAN and
> normally-signed HTTPS work without any setup.

## Roadmap
- **Multiple servers** — manage more than one qBittorrent server
- **Tags** — create new tags and assign them when adding a torrent
- **Settings screen** — in-app preferences to configure behaviours such as the status notification
- **Export/import config** — back up and restore server configurations and settings to a file

## Credits

Captain qBit began as a fork of [Yash-Garg/qBittorrent-Manager](https://github.com/Yash-Garg/qBittorrent-Manager)
and builds on that foundation. Thanks to the original author and contributors for their work.

## License

Licensed under the [GNU General Public License v3.0](LICENSE.txt) — the same license as the
upstream project, so this app and any derivatives stay GPL-3.0.

- Original work © [Yash Garg](https://github.com/Yash-Garg) and contributors
- Modifications © 2026 Aviad Gafni
