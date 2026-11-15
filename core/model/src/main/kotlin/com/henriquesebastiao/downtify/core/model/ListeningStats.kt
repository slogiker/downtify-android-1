package com.henriquesebastiao.downtify.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One play this phone could account for: a library song, the moment it started and the song's
 * length. [durationMs] is how long the song is, not the time actually heard; a play the player
 * only started counts with its whole length, as these totals always do.
 */
data class ListenEvent(
    val trackId: String,
    val title: String,
    val artist: String,
    val playedAt: Instant,
    val durationMs: Long,
)

/**
 * What the screen is asked about. [days] counts calendar days in the phone's time zone and
 * includes today — "last 7 days" is today and the six before it — so a play doesn't fall in or out
 * of the range as the day goes on. Null counts everything.
 */
enum class StatsRange(val days: Int?) {
    Last7Days(7),
    Last30Days(30),
    AllTime(null),
}

/** An artist and how many plays were counted for it. */
data class ArtistPlays(val artist: String, val plays: Int)

/** A song and how many plays were counted for it. */
data class TrackPlays(val trackId: String, val title: String, val artist: String, val plays: Int)

/** One bar of the activity chart: [date] as the phone's calendar reads it and the plays counted then. */
data class DailyPlays(val date: LocalDate, val plays: Int)

/**
 * What a range adds up to: the plays counted in it, the songs' total length, the artists among
 * them, the busiest artists and songs, and the last [CHART_DAYS] days of activity. [daily] always
 * has every day, zeros included, so the chart keeps its shape whether or not there was a play.
 */
data class ListeningStats(
    val songsPlayed: Int,
    val listeningTimeMs: Long,
    val distinctArtists: Int,
    val topArtists: List<ArtistPlays>,
    val topTracks: List<TrackPlays>,
    val daily: List<DailyPlays>,
) {
    val isEmpty: Boolean get() = songsPlayed == 0

    companion object {
        /** Nothing counted: the screen says so rather than draw zeros as if they were facts. */
        val Empty = ListeningStats(0, 0L, 0, emptyList(), emptyList(), emptyList())
    }
}

/**
 * Turns plays into what the screen shows. Pure: the caller passes the clock and the zone, so a test
 * pins both instead of racing the wall clock.
 *
 * A play dated after [now] is left out (a phone whose clock was ahead of the server's, a listen
 * reported with a future stamp): the totals only ever count plays that already happened.
 * Plays without an artist are counted as plays but not as artists — an unknown artist is not one
 * more of them.
 */
object ListeningStatsCalculator {
    /** How many artists and songs the screen names. */
    const val TOP_LIMIT = 5

    /** The chart's window, in days, ending today. */
    const val CHART_DAYS = 30

    fun calculate(
        events: List<ListenEvent>,
        range: StatsRange,
        now: Instant,
        zone: ZoneId = ZoneOffset.UTC,
        topLimit: Int = TOP_LIMIT,
    ): ListeningStats {
        val today = now.atZone(zone).toLocalDate()
        val played = events.filterNot { it.playedAt.isAfter(now) }
        val from = range.days?.let { today.minusDays((it - 1).toLong()) }
        val counted = played.filter { event ->
            from == null || !event.playedAt.atZone(zone).toLocalDate().isBefore(from)
        }
        if (counted.isEmpty()) return ListeningStats.Empty.copy(daily = chart(played, today, zone))
        return ListeningStats(
            songsPlayed = counted.size,
            listeningTimeMs = counted.sumOf { it.durationMs.coerceAtLeast(0L) },
            distinctArtists = counted.map { it.artist.trim() }.filter { it.isNotEmpty() }.distinct().size,
            topArtists = topArtists(counted, topLimit),
            topTracks = topTracks(counted, topLimit),
            daily = chart(played, today, zone),
        )
    }

    /** The last [CHART_DAYS] days, oldest first, over every play there is — not only the range's. */
    private fun chart(played: List<ListenEvent>, today: LocalDate, zone: ZoneId): List<DailyPlays> {
        val perDay = played.groupingBy { it.playedAt.atZone(zone).toLocalDate() }.eachCount()
        val first = today.minusDays((CHART_DAYS - 1).toLong())
        return (0 until CHART_DAYS).map { offset ->
            val date = first.plusDays(offset.toLong())
            DailyPlays(date, perDay[date] ?: 0)
        }
    }

    private fun topArtists(counted: List<ListenEvent>, limit: Int): List<ArtistPlays> = counted
        .map { it.artist.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { ArtistPlays(it.key, it.value) }

    private fun topTracks(counted: List<ListenEvent>, limit: Int): List<TrackPlays> = counted
        .groupingBy { it.trackId }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { entry ->
            val song = counted.first { it.trackId == entry.key }
            TrackPlays(entry.key, song.title, song.artist, entry.value)
        }
}
