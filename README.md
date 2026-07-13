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
- Filter by status, categories, tags, or trackers from a slide-in sidebar matching the qBittorrent desktop layout — active filters shown as dismissible chips
- Categories containing `/` are grouped into a collapsible tree (like folders)
- Each torrent shows its category and tags as chips, with a customisable colour per category
- Select multiple torrents to bulk-set category or tags, pause/resume, or delete them in one action
- Colour-coded status matching the qBittorrent Web UI (downloading, seeding, stopped, errored…)

### Adding torrents
- Add via magnet links or `.torrent` files, or open them from other apps
- Choose a category (pick an existing one or create a new one on the spot), set a save path,
  start paused, or enable Automatic Torrent Management
- Choose which files to download before adding — the file tree is read instantly from a
  `.torrent`, or fetched over the network for a magnet
- Detects duplicates before adding, so you don't re-add a torrent you already have

### Managing torrents
- Pause/resume, delete (optionally with data), force recheck, reannounce, and rename
- Set or create categories, manage tags, toggle Automatic Torrent Management, or change the save path
- Detail view with Info, Files, Trackers, and Peers tabs — browse the file tree, copy full paths,
  and set per-file download priorities (or skip files entirely)
- Surfaces the actual failure reason when a torrent errors, instead of a bare "error"

### Connection & reliability
- Works with qBittorrent 4.x and 5.x
- Optional HTTP Basic Auth for servers behind a reverse proxy
- Short, readable messages for network errors
- Optional status notification showing connection state and current speeds

### Servers & settings
- Save multiple servers and switch the active one from the navigation drawer
- Manage servers — add, edit, or remove them — from an in-app Settings screen
- Light, Dark, or System theme, plus optional Material You dynamic colours
- Notifications you control: toggle the ongoing status notification, and opt in to alerts
  when a download or a force-recheck finishes
- Encrypted backup — export and import all your servers and settings as a passphrase-protected file
- About screen with app version and project links

> **Self-signed HTTPS:** certificates are validated properly (no blanket "trust everything"
> option). To use a self-signed cert, install it on your device via **Settings → Security →
> Install a certificate** — the app then trusts it with full validation. Plain HTTP on a LAN and
> normally-signed HTTPS work without any setup.

## Roadmap
- **More in-app preferences** — configurable behaviours such as the refresh/polling interval

## Support

Captain qBit is free and open source. If you find it useful and want to support its
development, you can leave a tip on Ko-fi — entirely optional, and always appreciated.

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/acamol)

## Credits

Captain qBit began as a fork of [Yash-Garg/qBittorrent-Manager](https://github.com/Yash-Garg/qBittorrent-Manager)
and builds on that foundation. Thanks to the original author and contributors for their work.

## License

Licensed under the [GNU General Public License v3.0](LICENSE.txt) — the same license as the
upstream project, so this app and any derivatives stay GPL-3.0.

- Original work © [Yash Garg](https://github.com/Yash-Garg) and contributors
- Modifications © 2026 Aviad Gafni
