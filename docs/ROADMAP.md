# Roadmap

## Features

### Quick Settings tile

- [ ] Add a Quick Settings tile that toggles alternative speed limits on the selected server, with
  the tile state reflecting the current mode.

### Share to Captain qBit

- [ ] Accept `ACTION_SEND` text so magnet links and torrent URLs shared from any app open the
  add-torrent flow.

### All-servers dashboard

- [ ] Show combined transfer speeds and torrent counts across every saved server, with a way to jump
  into each server.

## Maintainability

### Review replacing Bonsai

- [ ] Evaluate actively maintained Compose tree-view components or platform-supported alternatives
  so the app does not need to maintain its local `:bonsai-core` implementation.

The review must cover torrent files, file selection when adding a torrent, and the RSS feed tree.
Compare maintenance activity, licensing, Compose and Material 3 Expressive compatibility,
accessibility, RTL and large-text support, and performance with large trees.

Any replacement must preserve folder expansion, search/filter behavior, whole-subtree checkbox
selection, and RSS drag-and-drop/move behavior. Record the recommendation and migration cost before
replacing Bonsai. If no suitable maintained alternative exists, document the gaps and tradeoffs.
