/*
 * Matchday Play — lembretes locais de partidas independentes do guia IPTV.
 * Base OpenTV, GPL-3.0-or-later.
 */
package app.opentv.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.opentv.MainActivity
import app.opentv.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/** Lembretes a 15 minutos do início, com horários de Portugal continental. */
object MatchdayFixtureAlerts {
    const val ACTION_FIRE = "app.opentv.matchday.FIXTURE_REMINDER"
    private const val PREFS = "matchday_fixtures"
    private const val ALERT_PREFIX = "alert_"
    private const val CHANNEL_PREFIX = "channel_"
    private const val CHANNEL_NAME_PREFIX = "channel_name_"
    private const val NOTIFICATION_CHANNEL = "matchday_games"

    fun key(fixture: MatchdayFixture): String =
        fixture.date + "|" + fixture.home + "|" + fixture.away

    private fun code(key: String): Int = key.hashCode() and 0x7fffffff
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedChannelId(context: Context, fixture: MatchdayFixture): Long? =
        prefs(context).getLong(CHANNEL_PREFIX + key(fixture), -1L).takeIf { it > 0L }

    fun selectedChannelName(context: Context, fixture: MatchdayFixture): String? =
        prefs(context).getString(CHANNEL_NAME_PREFIX + key(fixture), null)

    fun assignChannel(context: Context, fixture: MatchdayFixture, id: Long, name: String) {
        prefs(context).edit()
            .putLong(CHANNEL_PREFIX + key(fixture), id)
            .putString(CHANNEL_NAME_PREFIX + key(fixture), name)
            .apply()
        // Actualiza o canal num lembrete já existente sem alterar a hora do aviso.
        val key = key(fixture)
        val existing = prefs(context).getString(ALERT_PREFIX + key, null)
        if (existing != null) {
            runCatching {
                val data = JSONObject(existing).apply {
                    put("channelId", id)
                }
                prefs(context).edit().putString(ALERT_PREFIX + key, data.toString()).apply()
            }
        }
    }

    fun clearChannel(context: Context, fixture: MatchdayFixture) {
        prefs(context).edit()
            .remove(CHANNEL_PREFIX + key(fixture))
            .remove(CHANNEL_NAME_PREFIX + key(fixture))
            .apply()
        val key = key(fixture)
        val existing = prefs(context).getString(ALERT_PREFIX + key, null)
        if (existing != null) {
            runCatching {
                val data = JSONObject(existing).apply { put("channelId", -1L) }
                prefs(context).edit().putString(ALERT_PREFIX + key, data.toString()).apply()
            }
        }
    }

    fun kickoffMillis(fixture: MatchdayFixture): Long? {
        val time = fixture.time ?: return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Europe/Lisbon")
                isLenient = false
            }.parse(fixture.date + " " + time)?.time
        }.getOrNull()
    }

    fun hasReminder(context: Context, fixture: MatchdayFixture): Boolean =
        prefs(context).contains(ALERT_PREFIX + key(fixture))

    fun setReminder(context: Context, fixture: MatchdayFixture): Boolean {
        val kickoff = kickoffMillis(fixture) ?: return false
        val now = System.currentTimeMillis()
        if (kickoff <= now) return false
        val id = key(fixture)
        val fireAt = maxOf(now + 10_000L, kickoff - 15L * 60 * 1000)
        val payload = JSONObject().apply {
            put("key", id)
            put("title", fixture.home + " vs " + fixture.away)
            put("kickoff", kickoff)
            put("fireAt", fireAt)
            put("channelId", selectedChannelId(context, fixture) ?: -1L)
        }
        prefs(context).edit().putString(ALERT_PREFIX + id, payload.toString()).apply()
        schedule(context, payload)
        return true
    }

    fun cancelReminder(context: Context, fixture: MatchdayFixture) {
        val id = key(fixture)
        prefs(context).edit().remove(ALERT_PREFIX + id).apply()
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context, id))
    }

    /** Reposição após reinício: o Android apaga todos os alarmes num reboot. */
    fun reschedule(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        for ((k, v) in p.all) {
            if (!k.startsWith(ALERT_PREFIX)) continue
            val data = runCatching { JSONObject(v as String) }.getOrNull() ?: continue
            if (data.optLong("kickoff") <= now) {
                p.edit().remove(k).apply()
            } else {
                schedule(context, data)
            }
        }
    }

    fun deliver(context: Context, fixtureKey: String) {
        val p = prefs(context)
        val raw = p.getString(ALERT_PREFIX + fixtureKey, null) ?: return
        val info = runCatching { JSONObject(raw) }.getOrNull() ?: return
        // Só pode disparar uma vez.
        p.edit().remove(ALERT_PREFIX + fixtureKey).apply()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(NOTIFICATION_CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "Matchday Play — Jogos",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val channelId = info.optLong("channelId", -1L)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (channelId > 0L) putExtra(MainActivity.EXTRA_PLAY_CHANNEL, channelId)
        }
        val action = PendingIntent.getActivity(
            context, code(fixtureKey), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (channelId > 0L)
            "O jogo está quase a começar. Toca para abrir o canal que escolheste."
        else "O jogo está quase a começar. Abre a Matchday Play."
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_opentv_logo)
            .setContentTitle(info.optString("title", "Jogo de futebol"))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(action)
            .build()
        NotificationManagerCompat.from(context).notify(code(fixtureKey), notification)
    }

    private fun schedule(context: Context, data: JSONObject) {
        val key = data.optString("key")
        val start = data.optLong("fireAt")
        if (key.isBlank() || start <= 0L) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, key)
        val target = maxOf(System.currentTimeMillis() + 5_000L, start)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, target, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, target, pi)
        }
    }

    private fun pending(context: Context, key: String): PendingIntent {
        val intent = Intent(context, MatchdayFixtureAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("matchday://fixture/" + Uri.encode(key))
            putExtra("fixtureKey", key)
        }
        return PendingIntent.getBroadcast(
            context, code(key), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class MatchdayFixtureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> MatchdayFixtureAlerts.reschedule(context)
            MatchdayFixtureAlerts.ACTION_FIRE -> {
                val key = intent.getStringExtra("fixtureKey") ?: return
                MatchdayFixtureAlerts.deliver(context, key)
            }
        }
    }
}
