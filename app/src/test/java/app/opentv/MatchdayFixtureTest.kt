/*
 * Matchday Play — testes dos nomes de clubes entre competições.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv

import app.opentv.ui.MatchdayFixture
import app.opentv.ui.involvesClub
import app.opentv.ui.matchdayTeamKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatchdayFixtureTest {
    @Test
    fun benficaLigaEuropaMatching() {
        val europa = MatchdayFixture(
            date = "2026-11-26",
            time = "17:45",
            home = "Viktoria Plzeň",
            away = "Benfica",
            round = "5.ª jornada",
            competition = "Liga Europa",
        )
        assertThat(europa.involvesClub("Sport Lisboa e Benfica")).isTrue()
        assertThat(europa.involvesClub("SL Benfica")).isTrue()
        assertThat(europa.involvesClub("FC Porto")).isFalse()
        assertThat(matchdayTeamKey("SL Benfica"))
            .isEqualTo(matchdayTeamKey("Sport Lisboa e Benfica"))
    }

    @Test
    fun keepsPortoSportingSeparate() {
        val match = MatchdayFixture(
            date = "2026-11-29",
            time = "20:30",
            home = "FC Porto",
            away = "Sporting Clube de Portugal",
            round = "Jornada 12",
        )
        assertThat(match.involvesClub("Porto")).isTrue()
        assertThat(match.involvesClub("Sporting CP")).isTrue()
        assertThat(match.involvesClub("Benfica")).isFalse()
    }
}
