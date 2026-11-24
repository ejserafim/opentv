/*
 * Matchday Play — automatic fixture refresh with the app in the background.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.opentv.core.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Android owns the timing: periodic workers are inexact, and battery restrictions
 * may defer the run. The already-booked local reminders use AlarmManager.
 *
 * Only runs when the user has opted into automatic club alerts, never reads
 * IPTV credentials, and never sends the chosen club to an external service.
 */
class MatchdayFixtureSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("matchday_club_preferences", Context.MODE_PRIVATE)
        val selected = prefs.getString("favorite_club", null)
        val enabled = prefs.getBoolean("automatic_alerts", false)
        if (!enabled || selected.isNullOrBlank()) return Result.success()

        val client = ServiceLocator.get(applicationContext).httpClient
        val source = MatchdayFixtureSource(client)
        val fixtures = try {
            source.upcoming()
        } catch (err: Exception) {
            Log.w(TAG, "Fixture sync failed; existing alerts kept", err)
            return Result.retry()
        }

        // Only treat the calendar as current when the league feed was actually
        // fetched. A temporary offline response must not erase previous fixtures.
        if (source.lastLeagueLoadSucceeded) {
            MatchdayFixtureCache.save(applicationContext, fixtures)
        }

        // Re-read preferences in case the user turned alerts off while fetching.
        val refreshed = applicationContext.getSharedPreferences("matchday_club_preferences", Context.MODE_PRIVATE)
        val club = refreshed.getString("favorite_club", null)
            .takeIf { refreshed.getBoolean("automatic_alerts", false) }
        if (club != null) {
            val count = MatchdayFixtureAlerts.synchronizeAutomatic(
                applicationContext,
                fixtures,
                club,
                leagueLoaded = source.lastLeagueLoadSucceeded,
            )
            Log.i(TAG, "Club calendar refreshed: $count reminder(s) currently scheduled")
        }
        return if (source.lastLeagueLoadSucceeded) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "MatchdaySync"
        private const val WORK_NAME = "matchday-club-fixture-sync"

        /** Does not create any background work unless the user opted into alerts. */
        fun configure(context: Context) {
            val prefs = context.getSharedPreferences("matchday_club_preferences", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("automatic_alerts", false)
            val club = prefs.getString("favorite_club", null)
            val manager = WorkManager.getInstance(context)
            if (!enabled || club.isNullOrBlank()) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<MatchdayFixtureSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        }
    }
}
