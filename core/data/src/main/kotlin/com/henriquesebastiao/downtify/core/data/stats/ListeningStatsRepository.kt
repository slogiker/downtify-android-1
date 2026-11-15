package com.henriquesebastiao.downtify.core.data.stats

import com.henriquesebastiao.downtify.core.data.library.RecentsRepository
import com.henriquesebastiao.downtify.core.model.ListenEvent
import com.henriquesebastiao.downtify.core.model.RecentContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * What the listening stats screen reads.
 *
 * The phone keeps no log of what it played. A counted listen goes straight into the report queue
 * (`pending_listens`), which empties as [com.henriquesebastiao.downtify.core.data.listens.ListenReporter]
 * sends it, and `recent_contexts` keeps one row per album, artist or playlist that was started, not
 * one per song. [plays] therefore has nothing to answer: an empty flow is the honest reading until a
 * listen log exists, better than a send queue's leftovers passed off as history.
 *
 * [recentlyPlayed] is what the app really has — the same rows Home's "Jump back in" shows.
 */
@Singleton
class ListeningStatsRepository @Inject constructor(private val recents: RecentsRepository) {
    /** The plays the phone can account for, oldest first; empty until the app keeps a listen log. */
    fun plays(): Flow<List<ListenEvent>> = flowOf(emptyList())

    /** What was started lately, newest first. */
    fun recentlyPlayed(limit: Int = RECENT_LIMIT): Flow<List<RecentContext>> = recents.recents(limit)

    private companion object {
        const val RECENT_LIMIT = 20
    }
}
