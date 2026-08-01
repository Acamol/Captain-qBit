# Mock qBittorrent server

A dependency-free Python mock of the qBittorrent Web API, used to populate the
app with a curated demo dataset for **screenshots** (and general UI dev) without
needing a real qBittorrent instance or having to stage real torrent states.

It serves only the endpoints the app hits while browsing + the torrent-details
screen (`auth/login`, `sync/maindata`, `transfer/info`, `transfer/speedLimitsMode`,
`app/version`, `torrents/{info,properties,files,trackers}`, `sync/torrentPeers`) and returns a fixed set of
14 torrents across varied states, a category tree (`distros/*`, `public-domain/*`,
`creative-commons`, `datasets`), tags and trackers. All demo content is legal
(Linux ISOs, Creative-Commons films, public-domain books) — no piracy-looking data.

The torrent actions used by the UI are **stateful** — `torrents/delete` drops the
torrent, and `torrents/{stop,pause}` / `torrents/{start,resume}` flip its state —
so swipe and bulk pause/resume/delete are reflected on the next `sync/maindata`
poll. Other POST actions still return a generic `Ok.`.

## Test aids (for notification testing, no code edits needed)

- **Completion / recheck alerts**: resume the same downloading torrent twice
  (pause it, then resume-resume) to force it to finish, firing the "download
  complete" notification; do it again on a forced-complete torrent to send it
  back to downloading so you can repeat the test. Toggled via the app's own
  pause/resume actions.
- **RSS "new article" alerts**: tap **Refresh** on any feed in the app (Feeds
  tab's per-feed action, or the top bar in a feed's article list) - each
  refresh injects one new synthetic article into that feed, so the next RSS
  poll (Settings → RSS alert check interval) notices it and fires a
  notification. Repeatable indefinitely; each refresh adds one more article.

## Run

```bash
python3 tools/mock-server/mock_qbit.py          # binds 0.0.0.0:8080
MOCK_PORT=9090 python3 tools/mock-server/mock_qbit.py
```

It prints this machine's LAN IP on startup.

## Connect the app

Add a server in Captain qBit:

- Connection type: **HTTP**
- Host: the printed **LAN IP** (phone must be on the same Wi-Fi)
- Port: **8080**
- Username / password: **anything** (not validated)

If you change the dataset while the app is connected, **force-stop the app** (or
remove & re-add the server) before reconnecting — qBittorrent sync is incremental,
so a plain refresh keeps stale categories/torrents from the previous dataset.

There are no real credentials here: login accepts any username/password and
returns a dummy session cookie.
