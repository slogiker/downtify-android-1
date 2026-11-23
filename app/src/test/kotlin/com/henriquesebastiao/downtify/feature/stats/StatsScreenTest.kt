package com.henriquesebastiao.downtify.feature.stats

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.ListenEvent
import com.henriquesebastiao.downtify.core.model.ListeningStats
import com.henriquesebastiao.downtify.core.model.ListeningStatsCalculator
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.StatsRange
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A phone-sized screen: the lazy list only composes what fits.
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class StatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Three plays: two of one song, one of another — 7:30 of music, two artists. */
    private fun stats(daysAgo: Long = 1): ListeningStats {
        val now = Instant.now()
        val at = now.minus(daysAgo, ChronoUnit.DAYS)
        val events = listOf(
            ListenEvent("a", "Low Lights", "Nora Vale", at, 100_000),
            ListenEvent("a", "Low Lights", "Nora Vale", at.minus(1, ChronoUnit.HOURS), 100_000),
            ListenEvent("b", "Tides", "Lumen Field", at.minus(2, ChronoUnit.HOURS), 250_000),
        )
        return ListeningStatsCalculator.calculate(events, StatsRange.Last7Days, now)
    }

    private val recent = listOf(
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
            Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli(),
        ),
    )

    private fun state(
        stats: ListeningStats = stats(),
        range: StatsRange = StatsRange.Last7Days,
        recent: List<RecentContext> = this.recent,
    ) = StatsUiState(loading = false, range = range, stats = stats, recent = recent)

    @Test
    fun showsTheRangesTotalsAndItsTopArtists() {
        compose.setContent {
            DowntifyTheme { StatsScreen(state(), onRange = {}) }
        }
        compose.onNodeWithText("7 days").assertIsSelected()
        compose.onNodeWithText("30 days").assertIsNotSelected()
        compose.onNodeWithText("All time").assertIsNotSelected()
        // Songs played, their total length and the artists among them.
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("7:30").assertExists()
        compose.onNodeWithText("Songs played").assertExists()
        compose.onNodeWithText("Listening time").assertExists()
        compose.onNodeWithText("Artists").assertExists()
        compose.onNodeWithText("Top artists").assertExists()
        // The artist shows in the top list and again under recently played.
        compose.onAllNodesWithText("Nora Vale").onFirst().assertExists()
        compose.onAllNodesWithText("2 plays").onFirst().assertExists()
    }

    @Test
    fun aPhoneThatKeptNoHistorySaysSoAndShowsWhatItPlayed() {
        compose.setContent {
            DowntifyTheme {
                StatsScreen(state(stats = ListeningStats.Empty), onRange = {})
            }
        }
        compose.onNodeWithText("No listening history on this phone").assertExists()
        assertTrue(compose.onAllNodesWithText("Songs played").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Recently played").assertExists()
        compose.onNodeWithText("Harbor Nights").assertExists()
    }

    @Test
    fun aRangeWithoutPlaysSaysSoRatherThanCountingZeroes() {
        // Played ten days ago: outside this week, inside the chart's thirty days.
        compose.setContent {
            DowntifyTheme {
                StatsScreen(state(stats = stats(daysAgo = 10)), onRange = {})
            }
        }
        compose.onNodeWithText("Nothing played in this range").assertExists()
        assertTrue(compose.onAllNodesWithText("No listening history on this phone").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pickingARangeAsksForIt() {
        var picked: StatsRange? = null
        compose.setContent {
            DowntifyTheme {
                StatsScreen(state(), onRange = { picked = it })
            }
        }
        compose.onNodeWithText("30 days").performClick()
        assertEquals(StatsRange.Last30Days, picked)
    }

    @Test
    fun backLeavesTheScreen() {
        var back = 0
        compose.setContent {
            DowntifyTheme {
                StatsScreen(state(), onRange = {}, onBack = { back++ })
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }
}
