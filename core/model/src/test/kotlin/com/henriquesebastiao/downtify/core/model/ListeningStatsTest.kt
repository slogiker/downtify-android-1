package com.henriquesebastiao.downtify.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {
    // Fixed clock and zone: "today" is Sunday, 15 November 2026.
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun play(
        at: String,
        trackId: String = "t1",
        title: String = "Harbor Song",
        artist: String = "Nora Vale",
        durationMs: Long = 180_000,
    ) = ListenEvent(trackId, title, artist, Instant.parse(at), durationMs)

    private fun stats(events: List<ListenEvent>, range: StatsRange) =
        ListeningStatsCalculator.calculate(events, range, now, zone)

    @Test
    fun `seven days are today and the six before it`() {
        val events = listOf(
            play(at = "2026-11-15T00:30:00Z"),
            play(at = "2026-11-09T00:05:00Z"),
            // The seventh day back: outside a range that starts on the 9th.
            play(at = "2026-11-08T23:59:00Z"),
        )
        assertEquals(2, stats(events, StatsRange.Last7Days).songsPlayed)
        assertEquals(3, stats(events, StatsRange.AllTime).songsPlayed)
    }

    @Test
    fun `the counting time adds up the songs that were counted`() {
        val events = listOf(
            play(at = "2026-11-14T10:00:00Z", durationMs = 100_000),
            play(at = "2026-11-15T10:00:00Z", durationMs = 250_000),
            // Outside the range: its length is not counted either.
            play(at = "2026-10-01T10:00:00Z", durationMs = 900_000),
        )
        assertEquals(350_000L, stats(events, StatsRange.Last7Days).listeningTimeMs)
    }

    @Test
    fun `a play dated in the future is left out`() {
        val events = listOf(play(at = "2026-11-16T08:00:00Z"))
        val stats = stats(events, StatsRange.AllTime)
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.listeningTimeMs)
        assertTrue(stats.daily.all { it.plays == 0 })
    }

    @Test
    fun `artists and songs are ranked by plays and ties by name`() {
        val events = listOf(
            play(at = "2026-11-12T10:00:00Z", trackId = "b", title = "Tides", artist = "Lumen Field"),
            play(at = "2026-11-12T11:00:00Z", trackId = "c", title = "Paper Moon", artist = "Aria Sun"),
            play(at = "2026-11-14T10:00:00Z", trackId = "a", title = "Low Lights", artist = "Nora Vale"),
            play(at = "2026-11-15T10:00:00Z", trackId = "a", title = "Low Lights", artist = "Nora Vale"),
        )
        val stats = stats(events, StatsRange.Last30Days)
        // One play each for Aria Sun and Lumen Field: the tie is broken by name.
        assertEquals(
            listOf("Nora Vale" to 2, "Aria Sun" to 1, "Lumen Field" to 1),
            stats.topArtists.map { it.artist to it.plays },
        )
        // The songs tie the same way, by id.
        assertEquals(listOf("a" to 2, "b" to 1, "c" to 1), stats.topTracks.map { it.trackId to it.plays })
        assertEquals("Low Lights", stats.topTracks.first().title)
        assertEquals(3, stats.distinctArtists)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun `an artist named the same way twice is one artist`() {
        val events = listOf(
            play(at = "2026-11-13T10:00:00Z", trackId = "a", artist = "Nora Vale"),
            // Trailing space: the same artist, not one more.
            play(at = "2026-11-14T10:00:00Z", trackId = "b", artist = "Nora Vale "),
        )
        val stats = stats(events, StatsRange.Last7Days)
        assertEquals(1, stats.distinctArtists)
        assertEquals(1, stats.topArtists.size)
        assertEquals(2, stats.topArtists.first().plays)
    }

    @Test
    fun `a play without an artist counts but is not an artist`() {
        val events = listOf(play(at = "2026-11-14T10:00:00Z", artist = ""))
        val stats = stats(events, StatsRange.Last7Days)
        assertEquals(1, stats.songsPlayed)
        assertEquals(0, stats.distinctArtists)
        assertTrue(stats.topArtists.isEmpty())
    }

    @Test
    fun `the top lists stop at five`() {
        val events = (1..7).map { play(at = "2026-11-14T10:00:00Z", trackId = "t$it", artist = "Artist $it") }
        val stats = stats(events, StatsRange.AllTime)
        assertEquals(ListeningStatsCalculator.TOP_LIMIT, stats.topArtists.size)
        assertEquals(ListeningStatsCalculator.TOP_LIMIT, stats.topTracks.size)
    }

    @Test
    fun `the chart is thirty days ending today, empty days included`() {
        val events = listOf(
            play(at = "2026-11-15T09:00:00Z"),
            play(at = "2026-11-15T10:00:00Z"),
            play(at = "2026-11-01T10:00:00Z"),
        )
        val daily = stats(events, StatsRange.Last7Days).daily
        assertEquals(ListeningStatsCalculator.CHART_DAYS, daily.size)
        assertEquals(LocalDate.parse("2026-10-17"), daily.first().date)
        assertEquals(LocalDate.parse("2026-11-15"), daily.last().date)
        assertEquals(2, daily.first { it.date == LocalDate.parse("2026-11-15") }.plays)
        assertEquals(1, daily.first { it.date == LocalDate.parse("2026-11-01") }.plays)
        assertEquals(0, daily.first { it.date == LocalDate.parse("2026-11-10") }.plays)
        // The days are consecutive and in order, which is what the bars are drawn from.
        assertEquals(daily.map { it.date }.sorted(), daily.map { it.date })
    }

    @Test
    fun `a range without plays keeps the chart of what was played before it`() {
        val events = listOf(play(at = "2026-10-20T10:00:00Z"))
        val stats = stats(events, StatsRange.Last7Days)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.songsPlayed)
        assertTrue(stats.daily.any { it.plays == 1 })
        assertFalse(stats.isEmpty && stats.daily.all { it.plays == 0 })
    }

    @Test
    fun `no plays at all is empty for every range`() {
        StatsRange.entries.forEach { range ->
            val stats = stats(emptyList(), range)
            assertTrue(stats.isEmpty)
            assertEquals(0, stats.distinctArtists)
            assertTrue(stats.topArtists.isEmpty())
            assertTrue(stats.topTracks.isEmpty())
            assertEquals(ListeningStatsCalculator.CHART_DAYS, stats.daily.size)
        }
    }
}
