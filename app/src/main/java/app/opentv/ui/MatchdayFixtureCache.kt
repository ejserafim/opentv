/*
 * Matchday Play — local, non-sensitive cache for the public match calendar.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Calendar cache: local-only, no IPTV credentials, player URLs, or private user data.
 * The data is not considered verified once a network refresh fails: the UI labels it
 * as a locally stored snapshot.
 */
object MatchdayFixtureCache {
    private const val PREFS = "matchday_fixture_cache"
    private const val CONTENT = "fixtures"
    private const val SYNCED_AT = "synced_at"

    fun lastUpdatedMillis(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SYNCED_AT, 0L)

    fun read(context: Context): List<MatchdayFixture> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CONTENT, null) ?: return emptyList()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Europe/Lisbon")
        }.format(Date())

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(120, array.length())) {
                    val o = array.optJSONObject(i) ?: continue
                    val date = o.optString("date")
                    val home = o.optString("home")
                    val away = o.optString("away")
                    if (date.length != 10 || date < today || home.isBlank() || away.isBlank()) continue
                    add(
                        MatchdayFixture(
                            date = date,
                            time = o.optString("time").takeIf { it.isNotBlank() },
                            home = home,
                            away = away,
                            round = o.optString("round").takeIf { it.isNotBlank() },
                            competition = o.optString("competition").ifBlank { "Liga Portugal" },
                            broadcastChannel = o.optString("broadcastChannel").takeIf { it.isNotBlank() },
                            broadcastSource = o.optString("broadcastSource").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, fixtures: List<MatchdayFixture>) {
        val array = JSONArray()
        fixtures.take(120).forEach { fixture ->
            array.put(JSONObject().apply {
                put("date", fixture.date)
                put("time", fixture.time ?: "")
                put("home", fixture.home)
                put("away", fixture.away)
                put("round", fixture.round ?: "")
                put("competition", fixture.competition)
                put("broadcastChannel", fixture.broadcastChannel ?: "")
                put("broadcastSource", fixture.broadcastSource ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CONTENT, array.toString())
            .putLong(SYNCED_AT, System.currentTimeMillis())
            .apply()
    }
}
