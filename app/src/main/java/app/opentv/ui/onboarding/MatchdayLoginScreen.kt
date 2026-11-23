/*
 * Matchday Play — ecrã de entrada, adaptação GPL-3.0-or-later de OpenTV.
 * O endpoint é público dentro do APK; não contém credenciais de clientes.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel

private const val MATCHDAY_SERVER = "https://us7r.xyz:443"
private val Lime = Color(0xFF36F34B)
private val Ink = Color(0xFF06121D)

@Composable
fun MatchdayLoginScreen(
    viewModel: SourcesViewModel,
    onFinished: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val busy = ui.testing || ui.syncing

    Box(Modifier.fillMaxSize().background(Ink)) {
        StadiumBackdrop()
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x20000810),
                    0.45f to Color(0x92030C16),
                    1.0f to Color(0xCC020A11),
                ),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 550.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD7081620))
                    .padding(horizontal = 25.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MatchdayPlayMark(Modifier.size(86.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Matchday",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 34.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Play",
                                color = Lime,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 41.sp,
                            )
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(35.dp),
                                tint = Lime,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "FUTEBOL AO VIVO, FILMES E SÉRIES",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Utilizador") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = matchdayTextFieldColors(),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Palavra-passe") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Ocultar palavra-passe" else "Mostrar palavra-passe",
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = matchdayTextFieldColors(),
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        val source = Source(
                            name = "Matchday Play",
                            kind = SourceKind.XTREAM,
                            url = MATCHDAY_SERVER,
                            username = username.trim(),
                            password = password,
                        )
                        viewModel.loginMatchday(source) { success ->
                            if (success) onFinished()
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().height(57.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = Color(0xFF07150E),
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(23.dp),
                            color = Color(0xFF07150E),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ENTRAR", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }

                if (ui.testError != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Não foi possível iniciar sessão. Confirma os teus dados.",
                        color = Color(0xFFFFA6A6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (ui.syncing) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "A carregar os canais…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(25.dp))
                Text(
                    "TV EM DIRETO   •   DESPORTO   •   FILMES   •   SÉRIES",
                    fontSize = 11.sp,
                    color = Color(0xFFB7D3C4),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun matchdayTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Lime,
    unfocusedBorderColor = Color(0xFF809C9C),
    focusedLabelColor = Lime,
    unfocusedLabelColor = Color(0xFFCFDBD6),
    focusedLeadingIconColor = Lime,
    unfocusedLeadingIconColor = Color(0xFFE8EEE9),
    focusedTrailingIconColor = Color.White,
    unfocusedTrailingIconColor = Color.White,
    cursorColor = Lime,
    focusedContainerColor = Color(0xC1112632),
    unfocusedContainerColor = Color(0xA6112632),
)

/** Ilustração vetorial de estádio, sem dependências de imagens externas. */
@Composable
private fun StadiumBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF010B16), Color(0xFF071E28), Color(0xFF071A18)),
            ),
        )
        val pitch = h * 0.73f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF124324), Color(0xFF08341C), Color(0xFF031B12)),
                startY = pitch,
                endY = h,
            ),
            topLeft = Offset(0f, pitch),
            size = Size(w, h - pitch),
        )
        // Arquibancadas escuras com uma faixa luminosa ao longo do estádio.
        for (i in 0..3) {
            val y = pitch - h * 0.12f + i * h * 0.018f
            drawLine(
                Color(0xFF1EF06A).copy(alpha = 0.13f + i * 0.035f),
                Offset(0f, y + h * 0.045f),
                Offset(w, y + h * 0.045f),
                strokeWidth = 2.5f,
            )
        }
        // Linhas do relvado.
        val fieldWhite = Color.White.copy(alpha = 0.42f)
        drawLine(fieldWhite, Offset(w * 0.5f, pitch), Offset(w * 0.5f, h), strokeWidth = 2f)
        drawCircle(fieldWhite, radius = w * 0.11f, center = Offset(w * 0.5f, pitch + h * 0.12f), style = Stroke(2f))
        drawLine(fieldWhite, Offset(0f, pitch + h * 0.11f), Offset(w, pitch + h * 0.11f), strokeWidth = 2f)

        // Projetores e reflexos difusos.
        listOf(Offset(w * 0.11f, h * 0.12f), Offset(w * 0.89f, h * 0.12f)).forEach { pos ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55C6F8FF), Color(0x00C6F8FF)),
                    center = pos,
                    radius = w * 0.32f,
                ),
                radius = w * 0.32f,
                center = pos,
            )
            for (i in -2..2) {
                drawCircle(
                    Color(0xFFEAFFFF),
                    radius = (w / 210f).coerceAtLeast(2.5f),
                    center = Offset(pos.x + i * w / 90f, pos.y),
                )
            }
        }
    }
}

/** Símbolo da marca: campo de futebol contido num botão Play. */
@Composable
private fun MatchdayPlayMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.15f, h * 0.09f)
            lineTo(w * 0.89f, h * 0.50f)
            lineTo(w * 0.15f, h * 0.91f)
            close()
        }
        drawPath(p, Lime, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
        val lines = Color.White.copy(alpha = 0.90f)
        drawLine(lines, Offset(w * 0.30f, h * 0.34f), Offset(w * 0.68f, h * 0.50f), strokeWidth = w * 0.023f)
        drawLine(lines, Offset(w * 0.30f, h * 0.66f), Offset(w * 0.68f, h * 0.50f), strokeWidth = w * 0.023f)
        drawCircle(lines, radius = w * 0.10f, center = Offset(w * 0.45f, h * 0.50f), style = Stroke(w * 0.023f))
        drawCircle(Color.White, radius = w * 0.11f, center = Offset(w * 0.70f, h * 0.20f))
        drawCircle(Color(0xFF07131D), radius = w * 0.045f, center = Offset(w * 0.70f, h * 0.20f))
    }
}
