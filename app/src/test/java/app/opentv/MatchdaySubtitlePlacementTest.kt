/*
 * Matchday Play — testes de posicionamento das legendas.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv

import app.opentv.ui.player.matchdaySubtitleBottomFraction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatchdaySubtitlePlacementTest {
    @Test
    fun captionsStayAboveAndroidNavigationBarWhenControlsHidden() {
        val fraction = matchdaySubtitleBottomFraction(
            viewportHeightPx = 720,
            systemBottomInsetPx = 30,
            transportHeightPx = 300,
            transportVisible = false,
            televisionOrPip = false,
            marginPx = 42,
        )
        assertThat(fraction).isGreaterThan(0.15f)
        assertThat(fraction).isLessThan(0.20f)
    }

    @Test
    fun captionsRiseAboveMeasuredControlsWhenVisible() {
        val hidden = matchdaySubtitleBottomFraction(
            720, 30, 290, false, false, 40,
        )
        val visible = matchdaySubtitleBottomFraction(
            720, 30, 290, true, false, 40,
        )
        assertThat(visible).isGreaterThan(hidden)
        assertThat(visible).isWithin(0.001f).of(330f / 720f)
    }

    @Test
    fun tallTransportAndPanelsCannotPushCaptionsOutOfScreen() {
        val fraction = matchdaySubtitleBottomFraction(
            720, 30, 900, true, false, 40,
        )
        assertThat(fraction).isEqualTo(0.75f)
    }

    @Test
    fun televisionAndPictureInPictureKeepDefaultSubtitlePosition() {
        assertThat(matchdaySubtitleBottomFraction(720, 0, 300, true, true, 40))
            .isEqualTo(0.08f)
    }

    @Test
    fun initialLayoutHasSafeFallback() {
        assertThat(matchdaySubtitleBottomFraction(0, 30, 0, true, false, 40))
            .isEqualTo(0.16f)
    }
}
