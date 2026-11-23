/*
 * Matchday Play — adapted from OpenTV (GPL-3.0-or-later).
 * This test build intentionally uses a fixed Xtream endpoint and NO customer credentials.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel

private const val MATCHDAY_SERVER = "https://us7r.xyz:443"
private val MatchdayGreen = Color(0xFF25D779)
private val Midnight = Color(0xFF06150F)

@Composable
fun MatchdayLoginScreen(
    viewModel: SourcesViewModel,
    onFinished: () -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy = state.testing || state.syncing
    val canLogin = username.isNotBlank() && password.isNotBlank() && !busy

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF0C3020), Midnight, Color(0xFF030908)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .background(Color(0xE911241B), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "MATCHDAY",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Text(
                    "PLAY",
                    color = MatchdayGreen,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "FUTEBOL  ·  TV  ·  FILMES  ·  SÉRIES",
                    color = Color(0xFFB3D0C2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Utilizador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Palavra-passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    enabled = canLogin,
                    onClick = {
                        val source = Source(
                            name = "Matchday Play",
                            kind = SourceKind.XTREAM,
                            url = MATCHDAY_SERVER,
                            username = username.trim(),
                            password = password,
                        )
                        viewModel.loginMatchday(source) { ok -> if (ok) onFinished() }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatchdayGreen,
                        contentColor = Midnight,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = Midnight,
                        )
                    } else {
                        Text("ENTRAR", fontWeight = FontWeight.Bold)
                    }
                }
                if (state.testError != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Não foi possível entrar. Confirma o utilizador e a palavra-passe.",
                        color = Color(0xFFFF9D9D),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.syncing) {
                    Spacer(Modifier.height(14.dp))
                    Text("A carregar os canais…", color = Color.White)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Acede com as credenciais fornecidas.",
                    color = Color(0xFF9EB6A9),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
