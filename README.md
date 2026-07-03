# Captain qBit

An Android app for managing [qBittorrent](http://www.qbittorrent.org/) remotely, written in Kotlin.

> **Fork of [Yash-Garg/qBittorrent-Manager](https://github.com/Yash-Garg/qBittorrent-Manager)** — see below for what's new in this fork.

## What's new in this fork

### Torrent list

- **Sort** — sort the torrent list by name, size, progress, speed, ETA, ratio, or date added; tap again to reverse direction
- **Search** — filter torrents by name in real time via a collapsible search bar
- **Filter** — filter by state (downloading, seeding, stopped, etc.), category, tracker, or tags; active filters shown as dismissible chips
- **Speed display** — global download/upload speeds shown at the top of the torrent list

### Adding & managing torrents

- **Add-torrent options** — set category, save path, start-paused, and Automatic Torrent Management when adding a torrent
- **Open files & links externally** — opening a `.torrent` file or magnet link shows the add dialog prefilled, instead of adding silently
- **Detail-screen actions** — set category, manage tags, toggle Automatic Torrent Management, or change the save path from a torrent's detail screen
- **Full file paths** — long-press a file or folder in the Files tab to view and copy its full path
- **Remembered settings** — last-used sort option/direction and the add-torrent toggles persist between sessions

### Connection & reliability

- **HTTP Basic Auth** — optional username/password support for servers behind a reverse proxy
- **Edit server** — modify an existing server configuration without re-adding it
- **Friendlier errors** — network errors surface short, readable messages instead of raw stack traces
- **Throttled polling** — reduced request rate to avoid HTTP 429 (Too Many Requests)
- **qBittorrent 5.x compatibility** — added missing torrent states (`stoppedDL`, `stoppedUP`, `forcedMetaDL`)

### Under the hood

- **Build toolchain** — updated to AGP 9.2, Gradle 9.4, Kotlin 2.1; migrated from kapt to KSP

## Roadmap

- **Multiple servers** — managing more than one qBittorrent server
- **Settings screen** — in-app preferences to configure behaviours such as the persistent status notification
- **Export/import config** — back up and restore server configurations and settings to a file

## License

Licensed under the [GNU General Public License v3.0](LICENSE.txt), the same license as the
upstream project — this fork and any derivatives stay GPL-3.0.

- Original work © [Yash Garg](https://github.com/Yash-Garg) and contributors
- Modifications in this fork © 2026 Aviad Gafni
