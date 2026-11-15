# stats — handoff for the parent

The slice is self-contained except for four shared files. Nothing else is needed: no new Gradle
dependency, no manifest entry, no DI module (**`ListeningStatsRepository` is a `@Singleton` with an
`@Inject constructor`, like `RecentsRepository`, and `StatsViewModel` is a `@HiltViewModel`**), and
no new drawable (the side menu entry reuses `DowntifyIcons.History`).

Apply in this order.

---

## 1. `app/src/main/res/values/strings.xml`

**Anchor — insert directly after this line** (`<string name="downloads_title">Downloads</string>`,
line 200):

```xml
    <string name="downloads_title">Downloads</string>
```

**Snippet** (the `downloads_title` line is shown only to place it — insert the new lines after it,
don't repeat it):

```xml
    <string name="downloads_title">Downloads</string>

    <!-- Listening stats: the side menu's Listening screen -->
    <string name="stats_title">Listening</string>
    <string name="stats_range_7">7 days</string>
    <string name="stats_range_30">30 days</string>
    <string name="stats_range_all">All time</string>
    <string name="stats_songs_played">Songs played</string>
    <string name="stats_listening_time">Listening time</string>
    <string name="stats_artists">Artists</string>
    <string name="stats_top_artists">Top artists</string>
    <string name="stats_top_tracks">Top tracks</string>
    <string name="stats_chart">Last 30 days</string>
    <string name="stats_chart_empty">No plays to chart yet.</string>
    <string name="stats_recent">Recently played</string>
    <string name="stats_recent_empty">Nothing played yet. Albums, playlists and artists you start will show up here.</string>
    <string name="stats_empty_title">No listening history on this phone</string>
    <string name="stats_empty_body">Downtify counts plays on your server, but doesn\'t keep them on this phone, so there\'s nothing to add up yet. What you played lately is below.</string>
    <string name="stats_nothing_in_range">Nothing played in this range</string>
    <plurals name="stats_plays">
        <item quantity="one">%d play</item>
        <item quantity="other">%d plays</item>
    </plurals>
    <plurals name="stats_chart_a11y">
        <item quantity="one">%d play in the last 30 days</item>
        <item quantity="other">%d plays in the last 30 days</item>
    </plurals>
    <plurals name="stats_chart_caption">
        <item quantity="one">One bar a day. The busiest day had %d play.</item>
        <item quantity="other">One bar a day. The busiest day had %d plays.</item>
    </plurals>
```

**Translations.** The same four blocks go into `values-bg`, `values-el`, `values-es`, `values-fr`,
`values-hu`, `values-pt-rBR` and `values-tr` (lint fails on a missing translation). English values
above are the source; the three plurals need `one` + `other`, **plus `many` for `es`, `fr` and
`pt-BR`** (`stats_plays`, `stats_chart_a11y`, `stats_chart_caption`). In those three locales the
`many` item is the usual `%d de …` / `%d …` form — mirror whatever `songs_count` does there.

| key | English |
|---|---|
| `stats_title` | Listening |
| `stats_range_7` | 7 days |
| `stats_range_30` | 30 days |
| `stats_range_all` | All time |
| `stats_songs_played` | Songs played |
| `stats_listening_time` | Listening time |
| `stats_artists` | Artists |
| `stats_top_artists` | Top artists |
| `stats_top_tracks` | Top tracks |
| `stats_chart` | Last 30 days |
| `stats_chart_empty` | No plays to chart yet. |
| `stats_recent` | Recently played |
| `stats_recent_empty` | Nothing played yet. Albums, playlists and artists you start will show up here. |
| `stats_empty_title` | No listening history on this phone |
| `stats_empty_body` | Downtify counts plays on your server, but doesn't keep them on this phone, so there's nothing to add up yet. What you played lately is below. |
| `stats_nothing_in_range` | Nothing played in this range |
| `stats_plays` (plurals) | `one`: %d play · `other`: %d plays |
| `stats_chart_a11y` (plurals) | `one`: %d play in the last 30 days · `other`: %d plays in the last 30 days |
| `stats_chart_caption` (plurals) | `one`: One bar a day. The busiest day had %d play. · `other`: One bar a day. The busiest day had %d plays. |

---

## 2. `app/src/main/kotlin/com/henriquesebastiao/downtify/ui/navigation/Routes.kt`

**Anchor A — insert after this line** (line 15):

```kotlin
@Serializable data object DownloadsRoute
```

**Snippet A:**

```kotlin
@Serializable data object DownloadsRoute

/** What the phone can tell about what was played; from the side menu. */
@Serializable data object StatsRoute
```

**Anchor B — the `DrawerDestination` enum's `Downloads` entry** (line 70):

```kotlin
    Downloads(DownloadsRoute, R.string.nav_downloads, DowntifyIcons.Downloads, DowntifyIcons.DownloadsFilled),
```

**Snippet B** (the new entry goes between `Downloads` and `Trash`):

```kotlin
    Downloads(DownloadsRoute, R.string.nav_downloads, DowntifyIcons.Downloads, DowntifyIcons.DownloadsFilled),
    Stats(StatsRoute, R.string.stats_title, DowntifyIcons.History, DowntifyIcons.History),
```

---

## 3. `app/src/main/kotlin/com/henriquesebastiao/downtify/ui/DowntifyApp.kt`

**Anchor A — imports of the feature screen.** Insert after this line (line 95):

```kotlin
import com.henriquesebastiao.downtify.feature.similar.SimilarRoute as SimilarScreenRoute
```

**Snippet A:**

```kotlin
import com.henriquesebastiao.downtify.feature.similar.SimilarRoute as SimilarScreenRoute
import com.henriquesebastiao.downtify.feature.stats.StatsRoute
```

**Anchor B — imports of the navigation routes.** Insert after this line (line 125):

```kotlin
import com.henriquesebastiao.downtify.ui.navigation.SimilarRoute
```

**Snippet B:**

```kotlin
import com.henriquesebastiao.downtify.ui.navigation.SimilarRoute
import com.henriquesebastiao.downtify.ui.navigation.StatsRoute as StatsDestination
```

(No clash with the screen's `StatsRoute`: the route object is aliased, exactly as
`DownloadsRoute`/`DownloadsDestination` are.)

**Anchor C — the nav graph.** Insert after this line (line 384):

```kotlin
        composable<TrashDestination> { TrashScreenRoute(onBack = navController::popBackStack) }
```

**Snippet C:**

```kotlin
        composable<TrashDestination> { TrashScreenRoute(onBack = navController::popBackStack) }
        composable<StatsDestination> { StatsRoute(onBack = navController::popBackStack) }
```

The screen needs nothing else from the shell: `AppDrawer` already renders every
`DrawerDestination`, `DowntifyApp` already marks the selected one, and the screen's own back button
pops the stack like Downloads'. Covers are drawn through `LocalCoverUrls`, which the shell already
provides.

---

## 4. Nothing to add to `core/data` or `core/model` wiring

- `core/data/src/main/kotlin/…/core/data/stats/ListeningStatsRepository.kt` — `@Singleton` +
  `@Inject constructor(recents: RecentsRepository)`; Hilt needs no module for it.
- `core/model/src/main/kotlin/…/core/model/ListeningStats.kt` — pure Kotlin, no DI, no Android.
- `app/src/main/kotlin/…/feature/stats/StatsViewModel.kt` — `@HiltViewModel`.
- No Room schema change, no migration, no `DowntifyDatabase` version bump.

Verified by grep: `RecentsRepository` is `@Singleton class RecentsRepository @Inject constructor(
private val db: DowntifyDatabase)` (`core/data/.../library/RecentsRepository.kt:13-14`) and its
`recents(limit: Int): Flow<List<RecentContext>>` is public (line 15).
