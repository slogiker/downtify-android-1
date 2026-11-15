package com.henriquesebastiao.downtify.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.ChoiceGroup
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.KpiTile
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.SectionCard
import com.henriquesebastiao.downtify.core.designsystem.component.SectionTone
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.DailyPlays
import com.henriquesebastiao.downtify.core.model.ListenEvent
import com.henriquesebastiao.downtify.core.model.ListeningStats
import com.henriquesebastiao.downtify.core.model.ListeningStatsCalculator
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.StatsRange
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.formatDate
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun StatsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state = state, onRange = viewModel::selectRange, modifier = modifier, onBack = onBack)
}

/**
 * What the listener played, as far as the phone can tell: the plays counted in the chosen range,
 * the busiest artists and songs, the last thirty days day by day, and what was started lately.
 * Opened from the side menu, above Home.
 *
 * The totals are counted from the plays the app keeps. When it keeps none — which is where
 * Downtify stands today — the screen says so instead of drawing zeros, and "Recently played" is
 * what the phone really remembers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    onRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PageHeader(
                title = stringResource(R.string.stats_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        val stats = state.stats
        // Anything at all was played in the chart's window, even if not in this range.
        val anyHistory = stats.daily.any { it.plays > 0 }
        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.xl).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "range") { RangePicker(state.range, onRange) }
            if (stats.isEmpty) {
                item(key = "nothing") {
                    if (anyHistory) {
                        EmptyState(
                            icon = DowntifyIcons.History,
                            title = stringResource(R.string.stats_nothing_in_range),
                        )
                    } else {
                        EmptyState(
                            icon = DowntifyIcons.History,
                            title = stringResource(R.string.stats_empty_title),
                            body = stringResource(R.string.stats_empty_body),
                        )
                    }
                }
            } else {
                item(key = "totals") { Totals(stats) }
                if (stats.topArtists.isNotEmpty()) {
                    item(key = "artists") { TopArtists(stats) }
                }
                if (stats.topTracks.isNotEmpty()) {
                    item(key = "tracks") { TopTracks(stats) }
                }
            }
            item(key = "chart") { ActivityChart(stats) }
            item(key = "recent-title") { SectionTitle(stringResource(R.string.stats_recent)) }
            if (state.recent.isEmpty()) {
                item(key = "recent-empty") { RecentEmpty() }
            } else {
                items(state.recent, key = { "recent:${it.key}" }) { entry -> RecentRow(entry) }
            }
        }
    }
}

/** The range as three connected buttons, read as a choice of one. */
@Composable
private fun RangePicker(selected: StatsRange, onRange: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    ChoiceGroup(
        options = StatsRange.entries,
        selected = selected,
        label = { range -> stringResource(range.label()) },
        onSelect = onRange,
        role = Role.RadioButton,
        modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    )
}

private fun StatsRange.label(): Int = when (this) {
    StatsRange.Last7Days -> R.string.stats_range_7
    StatsRange.Last30Days -> R.string.stats_range_30
    StatsRange.AllTime -> R.string.stats_range_all
}

/** The range's three numbers, the screen's one hero card. */
@Composable
private fun Totals(stats: ListeningStats, modifier: Modifier = Modifier) {
    SectionCard(
        tone = SectionTone.Primary,
        hero = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile(
                value = stats.songsPlayed.toString(),
                label = stringResource(R.string.stats_songs_played),
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                value = formatDuration(stats.listeningTimeMs / MILLIS_PER_SECOND),
                label = stringResource(R.string.stats_listening_time),
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                value = stats.distinctArtists.toString(),
                label = stringResource(R.string.stats_artists),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TopArtists(stats: ListeningStats, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.stats_top_artists),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    ) {
        stats.topArtists.forEachIndexed { index, artist ->
            PlayCountRow(rank = index + 1, title = artist.artist, plays = artist.plays, first = index == 0)
        }
    }
}

@Composable
private fun TopTracks(stats: ListeningStats, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.stats_top_tracks),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    ) {
        stats.topTracks.forEachIndexed { index, track ->
            PlayCountRow(
                rank = index + 1,
                title = track.title,
                subtitle = track.artist,
                plays = track.plays,
                first = index == 0,
            )
        }
    }
}

/** A place in a top list: the rank, the name (and its artist) and how many plays it took. */
@Composable
private fun PlayCountRow(
    rank: Int,
    title: String,
    plays: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    first: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(top = if (first) 0.dp else Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            rank.toString(),
            style = DowntifyType.dataMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(RANK_WIDTH),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            pluralStringResource(R.plurals.stats_plays, plays, plays),
            style = DowntifyType.dataSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The last thirty days as bars: one a day, tallest for the busiest. It doesn't follow the range,
 * so a week with nothing in it still shows what the month looked like.
 */
@Composable
private fun ActivityChart(stats: ListeningStats, modifier: Modifier = Modifier) {
    val peak = stats.daily.maxOfOrNull { it.plays } ?: 0
    val total = stats.daily.sumOf { it.plays }
    SectionCard(
        title = stringResource(R.string.stats_chart),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    ) {
        if (peak == 0) {
            Text(
                stringResource(R.string.stats_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val a11y = pluralStringResource(R.plurals.stats_chart_a11y, total, total)
            Row(
                Modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = a11y },
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.daily.forEach { day ->
                    Bar(day, peak, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Text(
                pluralStringResource(R.plurals.stats_chart_caption, peak, peak),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One day's bar, grown from the bottom; a day with no plays draws nothing at all. */
@Composable
private fun Bar(day: DailyPlays, peak: Int, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (day.plays > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight((day.plays.toFloat() / peak).coerceIn(MIN_BAR, 1f))
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** An album, artist or playlist that was started lately: its cover, its name and the day. */
@Composable
private fun RecentRow(entry: RecentContext, modifier: Modifier = Modifier) {
    val title = if (entry.type == PlaybackContextType.Liked) stringResource(R.string.liked_songs) else entry.title
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(
            url = LocalCoverUrls.current.track(entry.coverTrackId),
            contentDescription = null,
            placeholderIcon = entry.type.icon(),
            modifier = Modifier.size(COVER),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatDate(entry.playedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun PlaybackContextType.icon(): Int = when (this) {
    PlaybackContextType.Album -> DowntifyIcons.Album
    PlaybackContextType.Artist -> DowntifyIcons.Artist
    PlaybackContextType.Playlist -> DowntifyIcons.Playlist
    PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
    PlaybackContextType.Songs -> DowntifyIcons.Song
}

@Composable
private fun RecentEmpty(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.stats_recent_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.screen),
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = Spacing.screen + Spacing.xs, end = Spacing.screen, top = Spacing.md)
            .semantics { heading() },
    )
}

private val COVER = 48.dp
private val RANK_WIDTH = 20.dp
private val CHART_HEIGHT = 96.dp
private val BAR_GAP = 3.dp
private const val MIN_BAR = 0.06f
private const val MILLIS_PER_SECOND = 1000.0

private fun previewStats(range: StatsRange = StatsRange.Last30Days): ListeningStats {
    val now = Instant.now()
    val events = listOf(
        "Nora Vale" to "Harbor Song",
        "Nora Vale" to "Low Lights",
        "Lumen Field" to "Tides",
        "Aria Sun" to "Paper Moon",
        "Nora Vale" to "Harbor Song",
        "Aria Sun" to "Paper Moon",
        "Odeon" to "Cobalt",
    ).mapIndexed { index, (artist, title) ->
        ListenEvent(
            trackId = "$artist/$title",
            title = title,
            artist = artist,
            playedAt = now.minus(index * 3L + 2L, ChronoUnit.HOURS),
            durationMs = 214_000,
        )
    }
    return ListeningStatsCalculator.calculate(events, range, now)
}

private val previewRecent = listOf(
    RecentContext(
        PlaybackContextType.Album,
        "alb-1",
        "Harbor Nights",
        null,
        Instant.now().minus(4, ChronoUnit.HOURS).toEpochMilli(),
    ),
    RecentContext(
        PlaybackContextType.Playlist,
        "Deep Focus",
        "Deep Focus",
        null,
        Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli(),
    ),
    RecentContext(
        PlaybackContextType.Liked,
        "",
        "",
        null,
        Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli(),
    ),
)

@PhonePreviews
@Composable
private fun StatsPreview() {
    DowntifyTheme {
        StatsScreen(
            state = StatsUiState(loading = false, stats = previewStats(), recent = previewRecent),
            onRange = {},
        )
    }
}

@PhonePreviews
@Composable
private fun StatsAllTimePreview() {
    DowntifyTheme {
        StatsScreen(
            state = StatsUiState(
                loading = false,
                range = StatsRange.AllTime,
                stats = previewStats(StatsRange.AllTime),
                recent = previewRecent,
            ),
            onRange = {},
        )
    }
}

@PhonePreviews
@Composable
private fun StatsEmptyPreview() {
    DowntifyTheme {
        StatsScreen(
            state = StatsUiState(loading = false, stats = ListeningStats.Empty, recent = emptyList()),
            onRange = {},
        )
    }
}
