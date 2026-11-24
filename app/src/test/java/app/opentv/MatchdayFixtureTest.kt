/*
 * Matchday Play — testes dos nomes de clubes entre competições.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv

import app.opentv.ui.MatchdayFixture
import app.opentv.ui.involvesClub
import app.opentv.ui.matchdayTeamKey
import app.opentv.ui.matchdayDisplayMerge
import app.opentv.ui.matchdayFixturesForClub
import app.opentv.ui.matchdayProgrammeMentionsClub
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

    @Test
    fun filterByFavoriteClubShowsOnlyBenficaAcrossCompetitions() {
        val games = listOf(
            MatchdayFixture(
                date = "2026-11-26",
                time = "17:45",
                home = "Viktoria Plzeň",
                away = "Benfica",
                round = "5.ª jornada",
                competition = "Liga Europa",
            ),
            MatchdayFixture(
                date = "2026-11-29",
                time = "20:30",
                home = "FC Porto",
                away = "Sporting CP",
                round = "12.ª jornada",
            ),
            MatchdayFixture(
                date = "2026-12-05",
                time = "18:00",
                home = "Sport Lisboa e Benfica",
                away = "SC Braga",
                round = "13.ª jornada",
            ),
        )
        val benficaOnly = matchdayFixturesForClub(games, "SL Benfica", onlyClub = true)
        assertThat(benficaOnly).hasSize(2)
        assertThat(benficaOnly.map { it.competition })
            .containsExactly("Liga Europa", "Liga Portugal").inOrder()
        assertThat(matchdayFixturesForClub(games, "SL Benfica", onlyClub = false))
            .containsExactlyElementsIn(games).inOrder()
    }

    @Test
    fun filterWithoutClubDoesNotHideGames() {
        val games = listOf(
            MatchdayFixture("2026-12-05", "18:00", "FC Porto", "SC Braga", null),
        )
        assertThat(matchdayFixturesForClub(games, null, onlyClub = true))
            .containsExactlyElementsIn(games)
    }

    @Test
    fun epgFavoriteClubTextMatchIsConservative() {
        assertThat(matchdayProgrammeMentionsClub("UEFA: Benfica vs Viktoria Plzen", "SL Benfica"))
            .isTrue()
        assertThat(matchdayProgrammeMentionsClub("FC Porto vs Sporting", "SL Benfica"))
            .isFalse()
        assertThat(matchdayProgrammeMentionsClub("Sporting CP vs Braga", "Sporting CP"))
            .isTrue()
    }


    @Test
    fun offlineLeagueCacheSurvivesWithNewEuropeanFixtures() {
        val league = MatchdayFixture(
            "2026-12-02", "20:00", "SL Benfica", "SC Braga",
            null, "Liga Portugal",
        )
        val europe = MatchdayFixture(
            "2026-12-10", "20:00", "Benfica", "OFI Creta",
            null, "Liga Europa",
        )
        assertThat(matchdayDisplayMerge(listOf(europe), listOf(league), false))
            .containsExactly(league, europe).inOrder()
        assertThat(matchdayDisplayMerge(listOf(europe), listOf(league), true))
            .containsExactly(europe)
    }

    @Test
    fun refreshedMatchTakesPriorityOverCachedDuplicate() {
        val old = MatchdayFixture("2026-12-02", "19:00", "Benfica", "Braga", null)
        val updated = old.copy(time = "20:00")
        assertThat(matchdayDisplayMerge(listOf(updated), listOf(old), false))
            .containsExactly(updated)
    }

}
