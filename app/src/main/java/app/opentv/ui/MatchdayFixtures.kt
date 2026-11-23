/*
 * Matchday Play — calendário independente do IPTV.
 *
 * Liga Portugal: openfootball/football.json (domínio público).
 * Jogos europeus adicionais: calendário confirmado nas páginas oficiais do
 * clube e da UEFA; são dados curados, não uma API dinâmica.
 *
 * Benfica: https://www.slbenfica.pt/pt-pt/agora/noticias/2026/08/29/futebol-benfica-calendario-jogos-fase-liga-liga-europa
 * UEFA: https://pt.uefa.com/uefaeuropaleague/clubs/50147--benfica/matches/
 * Transmissão de Viktoria Plzeň–Benfica:
 * https://www.sporttv.pt/noticias/871997/benfica-anuncia-bilhetes-para-visita-a-chequia
 * (SPORT TV 5, informação publicada a 11/11/2026).
 *
 * A presença do jogo NÃO implica disponibilidade da transmissão.
 * Jogos e horários podem mudar; numa versão futura deve ser usada uma API
 * de calendário licenciada, com sincronização e atualizações automáticas.
 * Base OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import java.io.IOException
import java.text.Normalizer
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
    val competition: String = "Liga Portugal",
    /** Emissora identificada numa fonte fiável, não inferida a partir de direitos gerais. */
    val broadcastChannel: String? = null,
    val broadcastSource: String? = null,
)

/** Identificadores de clubes variam entre calendários: "SL Benfica" e "Sport Lisboa e Benfica". */
internal fun matchdayTeamKey(name: String): String {
    val plain = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]"), "")
    return when (plain) {
        "sportlisboaebenfica", "slbenfica", "benfica", "sportlisboaebenficasad" -> "benfica"
        "sportingclubedeportugal", "sportingcp", "sporting", "sportinglisbon" -> "sporting"
        "futebolclubedoporto", "fcporto", "porto" -> "porto"
        "sportingclubedebraga", "scbraga", "braga" -> "braga"
        else -> plain
    }
}

fun MatchdayFixture.involvesClub(club: String): Boolean =
    matchdayTeamKey(home) == matchdayTeamKey(club) ||
        matchdayTeamKey(away) == matchdayTeamKey(club)

/** Conjunto pequeno e verificável até existir uma integração oficial para todas as provas. */
private fun verifiedEuropeanFixtures(season: String): List<MatchdayFixture> {
    if (season != "2026-27") return emptyList()
    return listOf(
        MatchdayFixture(
            date = "2026-11-26",
            time = "17:45", // Portugal continental, confirmado pelo SL Benfica
            home = "Viktoria Plzeň",
            away = "Benfica",
            round = "5.ª jornada",
            competition = "Liga Europa",
            broadcastChannel = "SPORT TV 5",
            broadcastSource = "SPORT TV · 11/11/2026",
        ),
        MatchdayFixture(
            date = "2026-12-10",
            time = "20:00", // Portugal continental, confirmado pelo SL Benfica
            home = "Benfica",
            away = "OFI Creta",
            round = "6.ª jornada",
            competition = "Liga Europa",
        ),
    )
}

/**
 * Agenda dos próximos 21 dias: Liga Portugal (OpenFootball) + europeus confirmados.
 * Se a liga estiver temporariamente indisponível, não escondemos os jogos europeus.
 */
class MatchdayFixtureSource(private val httpClient: OkHttpClient) {
    /** Impede apagar avisos da Liga quando o feed está indisponível. */
    @Volatile var lastLeagueLoadSucceeded: Boolean = false
        private set

    suspend fun upcoming(): List<MatchdayFixture> = withContext(Dispatchers.IO) {
        val portugal = TimeZone.getTimeZone("Europe/Lisbon")
        val calendar = Calendar.getInstance(portugal, Locale("pt", "PT"))
        val year = calendar.get(Calendar.YEAR)
        val startYear = if (calendar.get(Calendar.MONTH) < Calendar.JULY) year - 1 else year
        val season = startYear.toString() + "-" +
            ((startYear + 1) % 100).toString().padStart(2, '0')
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = portugal
            isLenient = false
        }
        val today = iso.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 21)
        val end = iso.format(calendar.time)
        val pattern = Regex("""\d{2}:\d{2}""")

        val leagueFixtures = try {
            loadPortugalLeague(season, today, end, pattern).also {
                lastLeagueLoadSucceeded = true
            }
        } catch (_: IOException) {
            lastLeagueLoadSucceeded = false
            emptyList()
        } catch (_: org.json.JSONException) {
            lastLeagueLoadSucceeded = false
            emptyList()
        }
        val european = verifiedEuropeanFixtures(season)
            .filter { it.date >= today && it.date <= end }
        if (leagueFixtures.isEmpty() && european.isEmpty()) {
            // A UI já sabe apresentar o erro, em vez de anunciar uma agenda completa.
            throw IOException("Não foi possível carregar jogos para este período")
        }
        (leagueFixtures + european)
            .distinctBy { it.date + matchdayTeamKey(it.home) + matchdayTeamKey(it.away) }
            .sortedWith(compareBy<MatchdayFixture> { it.date }.thenBy { it.time ?: "99:99" })
            .take(60)
    }

    private fun loadPortugalLeague(
        season: String,
        today: String,
        end: String,
        pattern: Regex,
    ): List<MatchdayFixture> {
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
            ?: throw IOException("Formato da Liga Portugal não reconhecido")
        return buildList {
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
                        competition = "Liga Portugal",
                    ),
                )
            }
        }
    }
}
