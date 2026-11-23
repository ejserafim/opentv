/*
 * Matchday Play — ficha do jogo, canal escolhido e lembrete local.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName

private val Highlight = Color(0xFF36F34B)
private val Backdrop = Color(0xFF122C33)
private val InfoColor = Color(0xFFB6C9C5)

/** O utilizador escolhe o canal; a app nunca o apresenta como emissor confirmado. */
@Composable
fun MatchdayFixtureDialog(
    fixture: MatchdayFixture,
    channels: List<Channel>,
    searchResults: List<Channel>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onPlay: (Channel) -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var choosingChannel by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val chosenId = remember(fixture, revision) {
        MatchdayFixtureAlerts.selectedChannelId(context, fixture)
    }
    val chosenName = remember(fixture, revision) {
        MatchdayFixtureAlerts.selectedChannelName(context, fixture)
    }
    val reminderOn = remember(fixture, revision) {
        MatchdayFixtureAlerts.hasReminder(context, fixture)
    }
    val kickoff = remember(fixture) { MatchdayFixtureAlerts.kickoffMillis(fixture) }
    val reminderPossible = kickoff != null && kickoff > System.currentTimeMillis()
    fun refresh() {
        revision++
        onChanged()
    }
    fun enableReminder() {
        if (MatchdayFixtureAlerts.setReminder(context, fixture)) {
            Toast.makeText(context, "Aviso ativado para este jogo.", Toast.LENGTH_SHORT).show()
            refresh()
        } else {
            Toast.makeText(context, "É necessário um horário futuro confirmado.", Toast.LENGTH_LONG).show()
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        if (allowed) enableReminder()
        else Toast.makeText(
            context,
            "Autoriza as notificações nas definições para receber avisos.",
            Toast.LENGTH_LONG,
        ).show()
    }
    fun toggleReminder() {
        if (reminderOn) {
            MatchdayFixtureAlerts.cancelReminder(context, fixture)
            Toast.makeText(context, "Aviso cancelado.", Toast.LENGTH_SHORT).show()
            refresh()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableReminder()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Backdrop,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 570.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = Highlight)
                    Spacer(Modifier.width(9.dp))
                    Text("FICHA DO JOGO", color = Highlight, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    fixture.competition + (fixture.round?.let { " · " + it } ?: ""),
                    color = Highlight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(fixture.home, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("VS", color = Highlight, fontWeight = FontWeight.Black)
                Text(fixture.away, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                val day = fixture.date.substring(8, 10) + "/" +
                    fixture.date.substring(5, 7) + "/" + fixture.date.substring(0, 4)
                Text(
                    day + " · " + (fixture.time?.let { it + " (Portugal)" } ?: "Hora por confirmar"),
                    color = InfoColor,
                    fontSize = 13.sp,
                )
                if (fixture.broadcastChannel != null) {
                    Text(
                        "ONDE VER EM PORTUGAL",
                        color = Highlight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        fixture.broadcastChannel,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Fonte: " + (fixture.broadcastSource ?: "publicação oficial") +
                            ". Informação sujeita a alterações.",
                        color = InfoColor,
                        fontSize = 12.sp,
                    )
                    OutlinedButton(
                        onClick = {
                            choosingChannel = true
                            query = fixture.broadcastChannel
                            onSearch(fixture.broadcastChannel)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Procurar " + fixture.broadcastChannel + " na minha lista")
                    }
                } else {
                    Text(
                        "Transmissão ainda não confirmada. Podes associar manualmente " +
                            "um canal da tua lista.",
                        color = InfoColor,
                        fontSize = 12.sp,
                    )
                }
                if (chosenId != null) {
                    Text("CANAL GUARDADO POR TI", color = Highlight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(chosenName ?: "Canal selecionado", color = Color.White, fontWeight = FontWeight.Bold)
                    val active = channels.firstOrNull { it.id == chosenId }
                    if (active != null) {
                        Button(
                            onClick = { onPlay(active); onDismiss() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Highlight, contentColor = Color(0xFF06121D),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("Abrir este canal")
                        }
                    }
                    TextButton(onClick = {
                        MatchdayFixtureAlerts.clearChannel(context, fixture)
                        refresh()
                    }) { Text("Remover canal associado") }
                }

                if (choosingChannel) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSearch(it)
                        },
                        label = { Text("Procurar canal desportivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val matches = (if (query.isBlank()) channels else searchResults)
                        .asSequence().take(24).toList()
                    if (matches.isEmpty()) {
                        Text(
                            if (query.isBlank()) "Escreve um canal, por exemplo Sport TV, DAZN ou BTV."
                            else "Sem canais nesta pesquisa.",
                            color = InfoColor,
                        )
                    }
                    matches.forEach { channel ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFF1A393B), RoundedCornerShape(8.dp))
                                .clickable {
                                    MatchdayFixtureAlerts.assignChannel(
                                        context, fixture, channel.id, channel.shownName,
                                    )
                                    choosingChannel = false
                                    query = ""
                                    onSearch("")
                                    refresh()
                                }
                                .padding(horizontal = 11.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(channel.shownName, color = Color.White, modifier = Modifier.weight(1f))
                            Text("Escolher", color = Highlight, fontSize = 12.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { choosingChannel = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (chosenId != null) "Alterar canal" else "Escolher canal")
                    }
                    if (channels.isEmpty()) {
                        Text(
                            "Também podes procurar canais noutras categorias da tua lista.",
                            color = InfoColor, fontSize = 12.sp,
                        )
                    }
                }

                if (reminderOn) {
                    Button(
                        onClick = { toggleReminder() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF315447), contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Aviso ativo · Cancelar")
                    }
                } else {
                    Button(
                        onClick = { toggleReminder() },
                        enabled = reminderPossible,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Highlight, contentColor = Color(0xFF06121D),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Avisar 15 min antes")
                    }
                    if (!reminderPossible) {
                        Text("Só é possível ativar avisos para jogos futuros com hora definida.", color = InfoColor, fontSize = 12.sp)
                    }
                }
                Text(
                    "O aviso é local no dispositivo. O Android pode atrasá-lo por poupança de bateria. " +
                        "Não confirma a disponibilidade da transmissão.",
                    color = InfoColor,
                    fontSize = 11.sp,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Fechar", color = Color.White)
                }
            }
        }
    }
}
