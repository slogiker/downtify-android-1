# stats

## The finding first: what listening data this app actually has

**The app keeps no listening history, and the server doesn't hand one back.** That decides the
slice, so here is the evidence, in order of what I checked.

1. **Room holds ten tables** (`core/data/schemas/…DowntifyDatabase/5.json`, `entities` in
   `core/data/src/main/kotlin/…/db/DowntifyDatabase.kt:8-18`): `tracks`, `sync_state`, `playlists`,
   `likes`, `recent_contexts`, `pending_listens`, `offline_collections`, `offline_files`,
   `playback_state`, `lyrics_offsets`. None of them is a listen log.
2. **`pending_listens` is a send queue, not history.** `ListenTracker` counts a play
   (`core/player/…/ListenTracker.kt:65-69`) → `ListenReporter.report()` inserts a row
   (`core/data/…/listens/ListenReporter.kt:34-44`) → `ListenFlusher` sends it and the row is
   **deleted** on success (`ListenReporter.flush()`, lines 55-69: `store.remove(playId)` →
   `dao.delete(playId)`; `PendingListenDao.delete`, `db/Daos.kt:115`). A play exists locally only
   until it has been reported. Using that table as "history" would draw a chart that shrinks as the
   phone goes online — exactly the kind of fake the slice must not ship.
3. **`recent_contexts` is real but not per-play.** One row per album/artist/playlist
   (`PlaybackContextType`, `core/model/…/RecentContext.kt:15`), keyed `"${type}:${refId}"` and
   **upserted** (`RecentsRepository.record`, line 27), so replaying an album overwrites its row
   instead of adding one, and the table is trimmed to 24 rows (`RecentsRepository.kt:28-33`,
   `KEEP = 24`). It answers "what did I start, and when" — not "how many songs did I play".
4. **Activity tracking is fire-and-forget to the server.** `ActivityTracker`
   (`core/player/…/ActivityTracker.kt`) posts to `/api/activity/playback` and keeps nothing;
   `PlaybackActivityReporter` drops a failed report on purpose (its KDoc, lines 32-36).
5. **The server has no endpoint that returns listens.** `POST /api/discover/listens`
   (`downtify-server/downtify/api.py:9689`) and `DELETE /api/discover/listens` (line 9735) are the
   only two routes for them; there is no `GET`. `GET /api/users/stats`
   (`downtify-server/downtify/account_routes.py:312`) is library counts (tracks, artists, albums),
   not plays. `docs/mobile-client-contract.md` documents no stats or listening endpoint at all —
   `grep -i "stats|listening"` over it returns nothing. `GET /api/monitor/artist-suggestions` does
   return per-artist `plays` for the signed-in user (`api.py` route, USER-readable per
   `auth.py:988`), but it is the Playlist Monitor's suggestion feed: artist-level, all-time,
   paged 20 at a time — not a stats source, and reaching it would mean new DTOs in `:core:network`.
   Left alone; see *Known gaps*.

So: **option (b)**. No server endpoint was invented and no numbers are made up. The screen is built
on what genuinely exists — the plays the app can account for (none today, behind one documented
seam) and `recent_contexts`, which is real, local and already shown on Home as "Jump back in".

## What it does (user-visible, and where it appears in the UI)

Side menu → **Listening** (a `DrawerDestination` entry, opened above Home, with a back button, like
Downloads). Top to bottom:

- **Range** — three connected buttons: 7 days, 30 days, all time. Kept across recreation.
- **Totals** — one hero card with three figures: songs played, listening time (the counted songs'
  lengths, `h:mm:ss` through `formatDuration`), distinct artists. Counted in the chosen range, in the
  phone's time zone, today included.
- **Top artists / Top tracks** — the five busiest in the range, each with its play count
  (`%d play` / `%d plays`).
- **Last 30 days** — a bar per day, tallest for the busiest, drawn from the app's own layout and
  `colorScheme.primary` (no chart library, no hard-coded colour); a caption says what the bars are.
  It deliberately does not follow the range, so an empty week still shows the month.
- **Recently played** — the real rows from `recent_contexts`: cover, name, the day it was played.

Honesty, in the interface:

- Nothing counted and never anything counted → *"No listening history on this phone"* and a body
  saying plays are counted on the server but not kept here, pointing at the list below. No zeros
  dressed up as data.
- Nothing counted in the selected range but plays exist outside it → *"Nothing played in this range"*
  (the 30-day chart still shows what the month looked like).
- Empty chart → *"No plays to chart yet."*, never empty bars implying silence.

## Files changed

| path | what |
|---|---|
| `core/model/src/main/kotlin/…/core/model/ListeningStats.kt` | new: `ListenEvent`, `StatsRange`, `ArtistPlays`, `TrackPlays`, `DailyPlays`, `ListeningStats`, `ListeningStatsCalculator` (pure; clock and zone injected) |
| `core/model/src/test/kotlin/…/core/model/ListeningStatsTest.kt` | new: the aggregation's unit tests |
| `core/data/src/main/kotlin/…/core/data/stats/ListeningStatsRepository.kt` | new: `plays()` (the seam, empty today) and `recentlyPlayed()` over `RecentsRepository` |
| `app/src/main/kotlin/…/feature/stats/StatsViewModel.kt` | new: `StatsUiState` + `@HiltViewModel`, range kept in `SavedStateHandle` |
| `app/src/main/kotlin/…/feature/stats/StatsScreen.kt` | new: `StatsRoute` + stateless `StatsScreen`, chart, rows, three `@PhonePreviews` |
| `app/src/test/kotlin/…/feature/stats/StatsScreenTest.kt` | new: Robolectric test of the stateless screen |
| `handoff/stats.md` | new: the shared-file snippets for the parent |
| `REPORT.md` | this file |

Nothing else is touched. No shared file was edited.

## Evidence that the APIs I call exist

Symbol → where it is defined (from grep in this worktree):

- `RecentsRepository` (`@Singleton`, `@Inject constructor(db: DowntifyDatabase)`) →
  `core/data/src/main/kotlin/…/library/RecentsRepository.kt:14`; `recents(limit): Flow<List<RecentContext>>`
  → `:15`.
- `RecentContext(type, refId, title, coverTrackId, playedAt)` and its `key` → `core/model/…/RecentContext.kt:4`, `:12`;
  `PlaybackContextType` (Album/Artist/Playlist/Liked/Songs) → `:15`.
- `formatDuration(seconds: Double): String` → `core/model/…/Formatting.kt:7`.
- `PageHeader(title, modifier, onBack, backLabel, titleColor, scrollBehavior, actions)` →
  `core/designsystem/…/component/PageHeader.kt:41`.
- `SectionCard(modifier, tone, hero, icon, overline, title, trailing, onClick, content)` →
  `…/component/Containers.kt:45`; `SectionTone` → `:37`; `KpiTile(value, label, modifier)` → `:162`.
- `EmptyState(icon, title, modifier, body, hero, action)` → `…/component/Components.kt:78`.
- `ChoiceGroup(options, selected, label, onSelect, modifier, role, containerColor)` →
  `…/component/ChoiceGroup.kt:47`.
- `Artwork(url, contentDescription, modifier, shape, placeholderIcon, tone, onReady, crossfade)` →
  `…/component/Artwork.kt:54`.
- `PhonePreviews` → `…/component/PhonePreviews.kt:16`.
- `Spacing` (xs/sm/md/screen/xs) → `…/theme/Shape.kt:151-162`; `ListStyle.Separated` → `…/theme/Shape.kt:100`;
  `DowntifyShape` corners not used directly (bars use `CircleShape`).
- `DowntifyType.dataMedium` / `dataSmall` → `…/theme/Type.kt:196`, `:199`.
- `DowntifyIcons.History` → `…/component/DowntifyIcons.kt:120`; `.Album` `:94`; `.Artist` `:96`;
  `.Song` `:100`; `.Playlist` `:114`; `.FavoriteFilled` `:43`.
- `LocalCoverUrls.current.track(id)` → `app/…/ui/common/CoverUrls.kt:27` (+ `CoverUrls.track`) — `CoverUrls.None` default, so previews and tests draw placeholders.
- `formatDate(epochMillis: Long)` → `app/…/ui/common/Dates.kt:17`.
- `PaddingValues.plusBottomBars()` → `app/…/ui/common/BottomBarsInset.kt:22`.
- `SavedStateHandle.getStateFlow` — same pattern as `DownloadsViewModel.kt:60`.
- `TopAppBarDefaults.pinnedScrollBehavior()` / `Scaffold(contentWindowInsets = WindowInsets(0))` —
  same pattern as `DownloadsScreen.kt` (`Scaffold`, `PageHeader(scrollBehavior = …)`).

## Assumptions I could not verify

Cannot compile here (no Android SDK), so:

- **`"listening time" = the counted songs' lengths**, not wall-clock time heard (`ListenEvent.durationMs`).
  The KDoc and the label say so; if the owner wants real listening seconds, that needs the log from
  *Known gaps*.
- **Range boundaries are calendar days in the phone's zone, today included** — "7 days" is today plus
  the six before. Pinned by tests, but the owner may prefer rolling 168 hours.
- **Plays dated in the future are ignored** (a phone clock ahead of the truth). Pinned by a test.
- A play with a **blank artist** counts as a play but not as an artist.
- `ZoneId.systemDefault()` is read once per ViewModel; a phone changing time zone while the screen is
  open keeps the old zone until it is opened again.
- Strings, routes and the drawer entry are not in this worktree — every one is listed in
  `handoff/stats.md`. **The slice does not compile until the parent applies them.**
- `@PhonePreviews` and the screen's `hero = true` totals card were not seen rendered; the layout
  follows `DownloadsScreen`'s shapes and spacing.

## What the parent must do (handoff items, DI, routes, strings)

All in `handoff/stats.md`, with quoted anchor lines:

1. `values/strings.xml` — 16 `stats_` strings + 3 plurals (`stats_plays`, `stats_chart_a11y`,
   `stats_chart_caption`), inserted after `downloads_title`; the same in the seven translations
   (`many` needed for es/fr/pt-BR).
2. `ui/navigation/Routes.kt` — `@Serializable data object StatsRoute` and the `DrawerDestination`
   entry `Stats(StatsRoute, R.string.stats_title, DowntifyIcons.History, DowntifyIcons.History)`.
3. `ui/DowntifyApp.kt` — two imports and
   `composable<StatsDestination> { StatsRoute(onBack = navController::popBackStack) }`.
4. **No DI, no Gradle, no manifest, no Room change.** `ListeningStatsRepository` is a concrete
   `@Singleton` with an `@Inject constructor` and `StatsViewModel` is `@HiltViewModel`, so Hilt needs
   no module — the same shape as `RecentsRepository`.

## Test coverage added

- `core/model/src/test/…/ListeningStatsTest.kt` (JUnit 4, pure JVM) — the seven-day window includes
  today and excludes the seventh day back; all time counts everything; plays dated in the future are
  dropped; lengths are summed only over counted plays; artists and tracks rank by plays with ties by
  name; the same artist with a trailing space is one artist; a blank artist counts as a play but not
  an artist; top lists stop at five; the chart has thirty consecutive days ending today with zero
  days filled and counts plays outside the range; a range with no plays keeps the chart of what came
  before it; no plays at all is empty for every range.
- `app/src/test/…/StatsScreenTest.kt` (Robolectric, JVM, `w411dp-h914dp`) — the selected range is
  marked and the others are not; the totals and top artists render (3 songs, `7:30`, 2 plays); a
  phone with no history shows the explanation and no totals, yet still lists what was played; a range
  with nothing in it says so and does not claim "no history"; picking a range reports it; back leaves
  the screen.

Both suites are written but **not run** — no SDK in this sandbox (`./gradlew` is off-limits per
PLAN.md §0). Because I cannot compile, the calculator's rules were re-implemented independently in
Python and run over the same ten cases: every expected value in `ListeningStatsTest` matches
(window boundaries, the future-play filter, duration sums, tie-breaks, the thirty buckets, a range
that is empty while the chart is not). That checks the arithmetic and the semantics, not the Kotlin.

## Known gaps

1. **The totals, top lists and chart never show real numbers today**, because no listen log exists
   (see the finding above). They render only when `ListeningStatsRepository.plays()` has events; the
   screen's empty states are what users will see. This is the slice's main gap and its main open
   decision:
   - *Cheapest real fix, no server work*: keep a local listen log. A new Room table (append on the
     same signal `ListenReporter` already gets, trim to ~90 days) plus its migration — deliberately
     **not** done here, because `Entities.kt`/`Daos.kt`/`DowntifyDatabase` version bumps are shared
     with the offline slice and a destructive fallback would lose user data. Then `plays()` gains a
     DAO query and the screen lights up; nothing else changes.
   - *Server-side alternative*: a `GET /api/v1/listens?since=` (or a stats endpoint) in the server
     repo. Nothing existing fits: `POST`/`DELETE /api/discover/listens` alone exist, and
     `/api/monitor/artist-suggestions` is artist-level, all-time and Monitor-shaped.
2. **Recently played is context-level and capped at 24 rows**, deduplicated per album/artist/playlist
   by design (`RecentsRepository.record` upserts), so it is a "last played" view, not a feed. The rows
   do not navigate anywhere: wiring them to albums/artists/playlists would need new callbacks in the
   nav graph, left to the parent if wanted.
3. **No deep link or shortcut** to the screen; it is reachable from the side menu only, as the slice
   asked.
4. `ListeningStatsRepository` has no test: it is two pass-throughs (one of them the documented empty
   seam), and its only real dependency is `RecentsRepository`, which has its own coverage elsewhere.
5. The chart has no date labels (only a caption and a screen-reader description) to keep it to a
   simple, dependency-free bar row.
