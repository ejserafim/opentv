/*
 * Matchday Play — botão Voltar visível em Android móvel.
 * OpenTV derivado sob GPL-3.0-or-later.
 */
package app.opentv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Só aparece no smartphone/tablet, e nunca esconde ou substitui o vídeo. */
@Composable
fun MatchdayMobileBackOverlay(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 10.dp, top = 10.dp)
                .clip(CircleShape)
                .background(Color(0xD3091820)),
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White,
            )
        }
    }
}
