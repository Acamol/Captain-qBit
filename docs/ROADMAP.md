# Roadmap

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
