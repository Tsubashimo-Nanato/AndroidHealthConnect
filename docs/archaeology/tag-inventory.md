# Tag Inventory

## Summary

Tags found: 2.

Chronological order is unambiguous because tagger dates, target commit dates, and commit topology all agree.

## Tags

| Stage | Tag | Type | Tag object | Target commit | Tagger date | Commit date | Commit subject | Reachable from `main` | Rough stage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `v0.1.0` | annotated | `dcd242a40f8d5afdd12f9f6601c0712a6a80b5e9` | `de6f474f7168f7a1d13f7aadca6d16c884e01ae8` | 2026-05-12 15:27:28 +0900 | 2026-05-12 15:27:08 +0900 | `Version 0.1.0` | yes | baseline Android plus .NET monorepo |
| 2 | `v0.1.1` | annotated | `5d8d5aaf16a098994143815157707834e94ad13e` | `95d9306e896dfd16627ef68f3d5fda0be587e78b` | 2026-06-05 22:00:48 +0900 | 2026-06-05 22:00:35 +0900 | `v0.1.1` | yes | sync/upload/UI improvement stage |

## Topology

Observed commit path:

```text
de6f474 (tag: v0.1.0) chore: prepare monorepo v0.1.0
bb0e08b Refactor health connect app flows and UI
a6c709a Optimize Health Connect app performance
95d9306 (tag: v0.1.1) Fix sync coverage and upload ranges
4a107ae (main) Optimize health data sync and upload
```

There is one current `main` commit after `v0.1.1`.
