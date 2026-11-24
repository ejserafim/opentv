/*
 * Matchday Play — safe subtitle positioning.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui.player

/**
 * Media3 SubtitleView's bottom padding is a fraction of the viewport height.
 *
 * For edge-to-edge Android phones we keep two lines clear of the gesture/navigation
 * bar. While the transport is visible, subtitles move above the actual measured
 * transport panel (including its system insets), not a fixed dp guess.
 *
 * TV and PiP retain the stock Media3 subtitle position.
 */
internal fun matchdaySubtitleBottomFraction(
    viewportHeightPx: Int,
    systemBottomInsetPx: Int,
    transportHeightPx: Int,
    transportVisible: Boolean,
    televisionOrPip: Boolean,
    marginPx: Int,
): Float {
    if (televisionOrPip) return 0.08f
    if (viewportHeightPx <= 0) return 0.16f

    // Extra room below two-line captions when the controls have faded away.
    val safelyAboveSystemBarPx = systemBottomInsetPx.coerceAtLeast(0) +
        (marginPx * 2).coerceAtLeast(0)

    // The entire Compose controls panel, including seek bar and insets, is reserved.
    val safelyAboveControlsPx =
        if (transportVisible) transportHeightPx.coerceAtLeast(0) + marginPx.coerceAtLeast(0)
        else 0

    val clearance = maxOf(safelyAboveSystemBarPx, safelyAboveControlsPx)
    return (clearance.toFloat() / viewportHeightPx).coerceIn(0.13f, 0.75f)
}
