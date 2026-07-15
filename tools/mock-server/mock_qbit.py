#!/usr/bin/env python3
"""
Mock qBittorrent Web API server for taking Captain qBit screenshots.

Serves just the endpoints the Android app hits during browsing + the torrent
details screen, returning a curated (and fully legal) demo dataset designed to
make the drawer counts, category tree, tags, trackers and the Statistics
dialog all look good.

Run:   python3 mock_qbit.py            # binds 0.0.0.0:8080
       MOCK_PORT=9090 python3 mock_qbit.py

Then in the app add a server:  connection HTTP, host = <this machine's LAN IP>,
port = 8080, username/password = anything (not checked).
"""
import hashlib
import json
import math
import os
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PORT = int(os.environ.get("MOCK_PORT", "8080"))

BASE_TS = 1719800000  # fixed "now-ish" epoch so timestamps are stable
INF_ETA = 8640000     # qBittorrent's "infinite" ETA sentinel

# ---- categories (tree via "/") -------------------------------------------
CATEGORIES = {
    "distros": "/downloads/distros",
    "distros/ubuntu": "/downloads/distros/ubuntu",
    "distros/debian": "/downloads/distros/debian",
    "distros/arch": "/downloads/distros/arch",
    "distros/fedora": "/downloads/distros/fedora",
    "creative-commons": "/downloads/creative-commons",
    "public-domain": "/downloads/public-domain",
    "public-domain/books": "/downloads/public-domain/books",
    "public-domain/audiobooks": "/downloads/public-domain/audiobooks",
    "datasets": "/downloads/datasets",
}
TAGS = ["iso", "hd", "favorite", "new", "verified"]

GiB = 1024 ** 3
MiB = 1024 ** 2

REQUIRED_TORRENT_KEYS = {
    "added_on", "amount_left", "auto_tmm", "availability", "category",
    "completed", "completion_on", "content_path", "dl_limit", "dlspeed",
    "downloaded", "downloaded_session", "eta", "f_l_piece_prio", "force_start",
    "hash", "last_activity", "magnet_uri", "max_ratio", "max_seeding_time",
    "name", "num_complete", "num_incomplete", "num_leechs", "num_seeds",
    "priority", "progress", "ratio", "ratio_limit", "save_path",
    "seeding_time_limit", "seen_complete", "seq_dl", "size", "state",
    "super_seeding", "tags", "time_active", "seeding_time", "total_size",
    "tracker", "up_limit", "uploaded", "uploaded_session", "upspeed",
}


def h(name):
    # 40-hex mock torrent id (BT v1 infohash shape). SHA-256 truncated — this is
    # just a deterministic identifier, not a security/crypto use, but avoid SHA-1.
    return hashlib.sha256(name.encode()).hexdigest()[:40]


def torrent(name, category, tags, state, size, progress, ratio=0.0,
            dlspeed=0, upspeed=0, num_seeds=0, num_leechs=0,
            tracker="", added_days=10, eta=INF_ETA):
    completed = size if progress >= 1.0 else int(size * progress)
    amount_left = size - completed
    uploaded = int(size * ratio)
    save_path = CATEGORIES.get(category, "/downloads")
    seeding = progress >= 1.0
    return {
        "added_on": BASE_TS - added_days * 86400,
        "amount_left": amount_left,
        "auto_tmm": False,
        "availability": 1.0 if seeding else round(0.7 + progress * 0.3, 3),
        "category": category,
        "completed": completed,
        "completion_on": (BASE_TS - (added_days - 1) * 86400) if seeding else -1,
        "content_path": f"{save_path}/{name}",
        "dl_limit": 0,
        "dlspeed": dlspeed,
        "downloaded": float(completed),
        "downloaded_session": float(min(completed, 256 * MiB)),
        "eta": eta,
        "f_l_piece_prio": False,
        "force_start": False,
        "hash": h(name),
        "last_activity": BASE_TS - 300,
        "magnet_uri": "magnet:?xt=urn:btih:" + h(name),
        "max_ratio": -1.0,
        "max_seeding_time": -1,
        "name": name,
        "num_complete": num_seeds + 20,
        "num_incomplete": num_leechs + 5,
        "num_leechs": num_leechs,
        "num_seeds": num_seeds,
        "priority": 0,
        "progress": float(progress),
        "ratio": float(ratio),
        "ratio_limit": -1.0,
        "save_path": save_path,
        "seeding_time_limit": -1,
        "seen_complete": BASE_TS - 600,
        "seq_dl": False,
        "size": size,
        "state": state,
        "super_seeding": False,
        "tags": ",".join(tags),
        "time_active": added_days * 86400,
        "seeding_time": (added_days - 1) * 86400 if seeding else 0,
        "total_size": size,
        "tracker": tracker,
        "up_limit": 0,
        "uploaded": uploaded,
        "uploaded_session": min(uploaded, 128 * MiB),
        "upspeed": upspeed,
    }


TR_UBUNTU = "https://torrent.ubuntu.com:6969/announce"
TR_DEBIAN = "https://bttracker.debian.org:6969/announce"
TR_ARCH = "https://tracker.archlinux.org:443/announce"
TR_FEDORA = "https://torrent.fedoraproject.org:443/announce"
TR_ARCHIVE = "https://bt1.archive.org:6969/announce"
TR_GUTEN = "https://tracker.gutenberg.org:443/announce"

_TORRENTS = [
    torrent("Ubuntu 24.04.1 Desktop (amd64)", "distros/ubuntu", ["iso", "verified"],
            "uploading", 6115295232, 1.0, ratio=3.42, upspeed=1240*1024,
            num_seeds=48, tracker=TR_UBUNTU, added_days=21),
    torrent("Debian 12.7.0 amd64 netinst", "distros/debian", ["iso"],
            "stalledUP", 660602880, 1.0, ratio=1.83, tracker=TR_DEBIAN, added_days=30),
    torrent("Arch Linux 2024.07.01 x86_64", "distros/arch", ["iso", "new"],
            "uploading", 987758592, 1.0, ratio=2.10, upspeed=640*1024,
            num_seeds=33, tracker=TR_ARCH, added_days=6),
    torrent("Fedora Workstation 40 (x86_64)", "distros/fedora", ["iso"],
            "downloading", 2211840000, 0.42, dlspeed=4820*1024,
            num_seeds=61, num_leechs=14, tracker=TR_FEDORA, added_days=1, eta=540),
    torrent("Big Buck Bunny (1080p, CC-BY)", "creative-commons", ["hd", "favorite"],
            "uploading", 355649536, 1.0, ratio=5.12, upspeed=320*1024,
            num_seeds=27, tracker=TR_ARCHIVE, added_days=40),
    torrent("Sintel (4K, CC-BY)", "creative-commons", ["hd"],
            "stalledUP", 4928307200, 1.0, ratio=0.94, tracker=TR_ARCHIVE, added_days=18),
    torrent("Tears of Steel (1080p, CC-BY)", "creative-commons", ["hd"],
            "downloading", 738197504, 0.73, dlspeed=2160*1024,
            num_seeds=19, num_leechs=8, tracker=TR_ARCHIVE, added_days=2, eta=180),
    torrent("Cosmos Laundromat (1080p, CC-BY)", "creative-commons", [],
            "stoppedDL", 1181116006, 0.15, tracker=TR_ARCHIVE, added_days=3),
    torrent("Blender Open Movies Pack", "creative-commons", ["favorite"],
            "metaDL", 21474836480, 0.0, tracker=TR_ARCHIVE, added_days=0, eta=INF_ETA),
    torrent("Pride and Prejudice (LibriVox)", "public-domain/audiobooks", [],
            "stalledUP", 682332160, 1.0, ratio=0.61, tracker=TR_ARCHIVE, added_days=55),
    torrent("Sherlock Holmes Collection (LibriVox)", "public-domain/audiobooks", ["favorite"],
            "uploading", 1476395008, 1.0, ratio=1.44, upspeed=210*1024,
            num_seeds=12, tracker=TR_ARCHIVE, added_days=33),
    torrent("Project Gutenberg Top 100 (epub)", "public-domain/books", ["verified"],
            "uploading", 524288000, 1.0, ratio=8.90, upspeed=96*1024,
            num_seeds=9, tracker=TR_GUTEN, added_days=70),
    torrent("NASA Apollo Imagery Archive", "datasets", [],
            "error", 9126805504, 0.88, tracker=TR_ARCHIVE, added_days=5),
    torrent("Wikipedia English Dump (2024)", "datasets", ["new"],
            "queuedDL", 24696061952, 0.0, tracker=TR_ARCHIVE, added_days=0),
]

for _t in _TORRENTS:
    missing = REQUIRED_TORRENT_KEYS - set(_t.keys())
    assert not missing, f"{_t['name']} missing keys: {missing}"

TORRENTS = {t["hash"]: t for t in _TORRENTS}

# current global speeds = sum of active torrents
_DL = sum(t["dlspeed"] for t in _TORRENTS)
_UP = sum(t["upspeed"] for t in _TORRENTS)

SERVER_STATE = {
    "alltime_dl": 4823449600000,      # ~4.4 TiB
    "alltime_ul": 11274289152000,     # ~10.2 TiB
    "average_time_queue": 0,
    "connection_status": "connected",
    "dht_nodes": 387,
    "dl_info_data": 3298534883,
    "dl_info_speed": _DL,
    "dl_rate_limit": 0,
    "free_space_on_disk": 549755813888,  # 512 GiB
    "global_ratio": "2.34",
    "queued_io_jobs": 0,
    "queueing": True,
    "read_cache_hits": "94.7",
    "read_cache_overload": "0",
    "refresh_interval": 1500,
    "total_buffers_size": 8388608,
    "total_peer_connections": 143,
    "total_queued_size": 0,
    "total_wasted_session": 12582912,
    "up_info_data": 7516192768,
    "up_info_speed": _UP,
    "up_rate_limit": 0,
    "use_alt_speed_limits": False,
    "write_cache_overload": "0",
}

MAINDATA = {
    "rid": 1,
    "full_update": True,
    "torrents": TORRENTS,
    "categories": {name: {"name": name, "savePath": path} for name, path in CATEGORIES.items()},
    "tags": TAGS,
    "server_state": SERVER_STATE,
}

_poll = 0


def maindata_response():
    """Fresh snapshot each poll: an incrementing rid + gently oscillating global
    speeds, like a real server. Without this the payload is byte-identical every
    poll and the app's StateFlow de-dupes it, so a pull-to-refresh never resets
    its spinner (the state never changes)."""
    global _poll
    _poll += 1
    ss = dict(SERVER_STATE)
    ss["dl_info_speed"] = max(0, int(_DL + math.sin(_poll / 2.0) * 300 * 1024))
    ss["up_info_speed"] = max(0, int(_UP + math.cos(_poll / 2.0) * 150 * 1024))
    resp = dict(MAINDATA)
    resp["rid"] = _poll
    resp["server_state"] = ss
    return resp


TRANSFER_INFO = {
    "dl_info_speed": _DL, "dl_info_data": SERVER_STATE["dl_info_data"],
    "up_info_speed": _UP, "up_info_data": SERVER_STATE["up_info_data"],
    "dl_rate_limit": 0, "up_rate_limit": 0, "dht_nodes": 387,
    "connection_status": "connected",
}

# ---- torrent details data (files/properties/trackers) --------------------
_MULTI_FILE = "Blender Open Movies Pack"


def files_for(hash_):
    t = TORRENTS.get(hash_)
    if t and t["name"] == _MULTI_FILE:
        names = [
            "Blender Open Movies/Big Buck Bunny/big_buck_bunny_1080p.mp4",
            "Blender Open Movies/Sintel/sintel_4k.mp4",
            "Blender Open Movies/Tears of Steel/tears_of_steel_1080p.mkv",
            "Blender Open Movies/Cosmos Laundromat/cosmos_laundromat.mp4",
            "Blender Open Movies/README.txt",
            "Blender Open Movies/LICENSE.txt",
        ]
        sizes = [355649536, 4928307200, 738197504, 1181116006, 4096, 2048]
        out = []
        start = 0
        for i, (n, s) in enumerate(zip(names, sizes)):
            end = start + max(1, s // (256 * 1024))
            out.append({"index": i, "name": n, "size": s, "progress": 0.0,
                        "priority": 1, "piece_range": [start, end],
                        "availability": 0.0, "is_seed": False})
            start = end + 1
        return out
    ext = ".iso" if (t and "iso" in t["tags"]) else ".mp4"
    name = (t["name"] if t else "file") + ext
    size = t["size"] if t else 1024
    prog = t["progress"] if t else 1.0
    return [{"index": 0, "name": name, "size": size, "progress": prog,
             "priority": 1, "piece_range": [0, max(1, size // (256 * 1024))],
             "availability": 1.0, "is_seed": prog >= 1.0}]


def properties_for(hash_):
    t = TORRENTS.get(hash_) or {}
    size = t.get("size", 0)
    return {
        "save_path": t.get("save_path", "/downloads"),
        "creation_date": BASE_TS - 90 * 86400, "piece_size": 4 * MiB,
        "comment": "Demo torrent for Captain qBit screenshots",
        "total_wasted": 1048576, "total_uploaded": t.get("uploaded", 0),
        "total_uploaded_session": 33554432,
        "total_downloaded": int(t.get("downloaded", 0)),
        "total_downloaded_session": 16777216,
        "up_limit": -1, "dl_limit": -1,
        "time_elapsed": t.get("time_active", 0), "seeding_time": t.get("seeding_time", 0),
        "nb_connections": 12, "nb_connections_limit": 100,
        "share_ratio": t.get("ratio", 0.0),
        "addition_date": t.get("added_on", BASE_TS),
        "completion_date": t.get("completion_on", -1),
        "created_by": "qBittorrent v4.6.0", "dl_speed_avg": 2202009,
        "dl_speed": t.get("dlspeed", 0), "eta": t.get("eta", INF_ETA),
        "last_seen": BASE_TS - 120, "peers": t.get("num_leechs", 0),
        "peers_total": t.get("num_incomplete", 0),
        "pieces_have": int(max(1, size // (4 * MiB)) * t.get("progress", 1.0)),
        "pieces_num": max(1, size // (4 * MiB)), "reannounce": 1500,
        "seeds": t.get("num_seeds", 0), "seeds_total": t.get("num_complete", 0),
        "total_size": size, "up_speed_avg": 524288, "up_speed": t.get("upspeed", 0),
    }


def trackers_for(hash_):
    t = TORRENTS.get(hash_) or {}
    rows = [
        {"url": "** [DHT] **", "status": 2, "tier": -1, "num_peers": 42,
         "num_seeds": 30, "num_leeches": 12, "num_downloaded": 0, "msg": ""},
        {"url": "** [PeX] **", "status": 2, "tier": -1, "num_peers": 8,
         "num_seeds": 5, "num_leeches": 3, "num_downloaded": 0, "msg": ""},
        {"url": "** [LSD] **", "status": 2, "tier": -1, "num_peers": 0,
         "num_seeds": 0, "num_leeches": 0, "num_downloaded": 0, "msg": ""},
    ]
    if t.get("tracker"):
        rows.append({"url": t["tracker"], "status": 2, "tier": 0,
                     "num_peers": t.get("num_leechs", 0) + t.get("num_seeds", 0),
                     "num_seeds": t.get("num_seeds", 0),
                     "num_leeches": t.get("num_leechs", 0),
                     "num_downloaded": t.get("num_complete", 0), "msg": "working"})
    return rows


# ---- torrent list (torrents/info) + peers (sync/torrentPeers) -------------
def torrents_info(qs):
    """The GET /torrents/info list endpoint (used by the background StatusWorker).
    Honors the optional `hashes` filter; otherwise returns every torrent."""
    hashes = (qs.get("hashes") or [""])[0]
    if hashes:
        wanted = set(hashes.split("|"))
        return [t for t in _TORRENTS if t["hash"] in wanted]
    return _TORRENTS


# TEST-NET documentation IPs (RFC 5737) — never real peers.
_PEERS = {
    "203.0.113.10:51413": {
        "client": "qBittorrent 4.6.0", "connection": "μTP", "country": "Germany",
        "country_code": "de", "dl_speed": 0, "downloaded": 734003200, "files": "",
        "flags": "U E", "flags_desc": "U: peer is unchoked\nE: uses μTP",
        "ip": "203.0.113.10", "port": 51413, "progress": 1.0, "relevance": 0.0,
        "up_speed": 245760, "uploaded": 104857600,
    },
    "198.51.100.23:6881": {
        "client": "Transmission 4.0.5", "connection": "BT", "country": "United States",
        "country_code": "us", "dl_speed": 0, "downloaded": 466616320, "files": "",
        "flags": "D", "flags_desc": "D: currently downloading",
        "ip": "198.51.100.23", "port": 6881, "progress": 0.62, "relevance": 0.38,
        "up_speed": 131072, "uploaded": 52428800,
    },
    "192.0.2.44:49152": {
        "client": "Deluge 2.1.1", "connection": "μTP", "country": "Japan",
        "country_code": "jp", "dl_speed": 0, "downloaded": 88080384, "files": "",
        "flags": "K", "flags_desc": "K: peer unchoked, but not interested",
        "ip": "192.0.2.44", "port": 49152, "progress": 0.14, "relevance": 0.11,
        "up_speed": 40960, "uploaded": 8388608,
    },
}

_peer_poll = 0


def peers_for(hash_):
    """A full-update peers snapshot each poll with a fresh rid (mirrors maindata),
    so the sync loop always merges to the same set."""
    global _peer_poll
    _peer_poll += 1
    return {"full_update": True, "rid": _peer_poll, "show_flags": True, "peers": _PEERS}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # quiet

    def _send(self, body, ctype="application/json", code=200, cookie=False):
        if not isinstance(body, (bytes, bytearray)):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        if cookie:
            self.send_header("Set-Cookie", "SID=mockmockmock; path=/; HttpOnly")
        self.end_headers()
        self.wfile.write(body)

    def _qs(self):
        return parse_qs(urlparse(self.path).query)

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/api/v2/auth/login":
            self._send("Ok.", "text/plain", cookie=True)
            return

        # Stateful torrent actions so the UI (swipe pause/resume/delete, bulk
        # actions) reflects the change on the next maindata poll. Body is
        # x-www-form-urlencoded; hashes are "|"-joined.
        length = int(self.headers.get("Content-Length") or 0)
        form = parse_qs(self.rfile.read(length).decode() if length else "")
        hashes = [h for h in (form.get("hashes", [""])[0]).split("|") if h]

        if path == "/api/v2/torrents/delete":
            for h in hashes:
                TORRENTS.pop(h, None)
        elif path in ("/api/v2/torrents/stop", "/api/v2/torrents/pause"):
            for h in hashes:
                t = TORRENTS.get(h)
                if t:
                    t["state"] = "stoppedUP" if t["progress"] >= 1.0 else "stoppedDL"
                    t["dlspeed"] = 0
                    t["upspeed"] = 0
        elif path in ("/api/v2/torrents/start", "/api/v2/torrents/resume"):
            for h in hashes:
                t = TORRENTS.get(h)
                if t:
                    t["state"] = "uploading" if t["progress"] >= 1.0 else "downloading"

        self._send("Ok.", "text/plain")  # generic success for any other action

    def do_GET(self):
        path = urlparse(self.path).path
        qs = self._qs()
        if path == "/api/v2/sync/maindata":
            self._send(json.dumps(maindata_response()))
        elif path == "/api/v2/transfer/info":
            self._send(json.dumps(TRANSFER_INFO))
        elif path == "/api/v2/transfer/speedLimitsMode":
            self._send("0", "text/plain")  # 0 = normal limits (alt speed off)
        elif path == "/api/v2/app/version":
            self._send("v4.6.0", "text/plain")
        elif path == "/api/v2/app/webapiVersion":
            self._send("2.9.3", "text/plain")
        elif path == "/api/v2/torrents/properties":
            self._send(json.dumps(properties_for((qs.get("hash") or [""])[0])))
        elif path == "/api/v2/torrents/files":
            self._send(json.dumps(files_for((qs.get("hash") or [""])[0])))
        elif path == "/api/v2/torrents/trackers":
            self._send(json.dumps(trackers_for((qs.get("hash") or [""])[0])))
        elif path == "/api/v2/torrents/info":
            self._send(json.dumps(torrents_info(qs)))
        elif path == "/api/v2/sync/torrentPeers":
            self._send(json.dumps(peers_for((qs.get("hash") or [""])[0])))
        elif path == "/api/v2/auth/logout":
            self._send("Ok.", "text/plain")
        else:
            # Unknown GET: log so we can spot anything the app needs but we missed.
            import sys
            print(f"[mock] unhandled GET {path}", file=sys.stderr)
            self._send("Ok.", "text/plain")


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


if __name__ == "__main__":
    ip = lan_ip()
    print(f"Mock qBittorrent API on http://0.0.0.0:{PORT}")
    print(f"In the app add a server -> HTTP, host = {ip}, port = {PORT}, user/pass = anything")
    print(f"{len(TORRENTS)} torrents, {len(CATEGORIES)} categories, {len(TAGS)} tags")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
