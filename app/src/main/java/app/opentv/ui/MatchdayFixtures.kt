/*
 * Matchday Play — calendário independente do IPTV.
 * Dados: openfootball/football.json, domínio público (CC0).
 * https://github.com/openfootball/football.json
 * A presença no calendário NÃO implica disponibilidade de transmissão.
 * Base OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class MatchdayFixture(
    val date: String,
    val time: String?,
    val home: String,
    val away: String,
    val round: String?,
)

/** Primeira Liga da época corrente, sem qualquer credencial ou informação do cliente. */
class MatchdayFixtureSource(private val httpClient: OkHttpClient) {
    suspend fun upcoming(): List<MatchdayFixture> = withContext(Dispatchers.IO) {
        val portugal = TimeZone.getTimeZone("Europe/Lisbon")
        val calendar = Calendar.getInstance(portugal, Locale("pt", "PT"))
        val year = calendar.get(Calendar.YEAR)
        val startYear = if (calendar.get(Calendar.MONTH) < Calendar.JULY) year - 1 else year
        val season = startYear.toString() + "-" +
            ((startYear + 1) % 100).toString().padStart(2, '0')
        val url = "https://raw.githubusercontent.com/openfootball/football.json/master/" +
            season + "/pt.1.json"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MatchdayPlay/1.0")
            .get()
            .build()
        val json = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Calendário indisponível: HTTP " + response.code)
            }
            response.body?.string() ?: throw IOException("Resposta sem calendário")
        }

        val list = JSONObject(json).optJSONArray("matches")
            ?: throw IOException("Formato não reconhecido")
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = portugal
            isLenient = false
        }
        val today = iso.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 21)
        val end = iso.format(calendar.time)
        val pattern = Regex("""\d{2}:\d{2}""")

        buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val date = item.optString("date").trim()
                val home = item.optString("team1").trim()
                val away = item.optString("team2").trim()
                if (date.length != 10 || date < today || date > end ||
                    home.isEmpty() || away.isEmpty()) continue
                add(
                    MatchdayFixture(
                        date = date,
                        time = item.optString("time").trim().takeIf { it.matches(pattern) },
                        home = home,
                        away = away,
                        round = item.optString("round").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.distinctBy { it.date + it.home + it.away }
            .sortedWith(compareBy<MatchdayFixture> { it.date }.thenBy { it.time ?: "99:99" })
            .take(40)
    }
}
