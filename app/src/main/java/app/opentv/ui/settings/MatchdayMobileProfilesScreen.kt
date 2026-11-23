/*
 * Matchday Play — perfis adaptados ao ecrã tátil.
 * OpenTV derivado sob GPL-3.0-or-later.
 */
package app.opentv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.ui.ProfilesViewModel

private val Dark = Color(0xFF06121D)
private val CardBackground = Color(0xFF122B30)
private val Green = Color(0xFF36F34B)
private val Secondary = Color(0xFFBBD1CB)
private const val NEW = -1L

@Composable
fun MatchdayMobileProfilesScreen(
    onBack: () -> Unit,
    viewModel: ProfilesViewModel,
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    fun saveProfile() {
        val id = editingId ?: return
        val name = nameInput.trim()
        if (name.isBlank()) return
        if (id == NEW) viewModel.addProfile(name) else viewModel.rename(id, name)
        editingId = null
    }
    fun openEditor(id: Long, currentName: String) {
        nameInput = currentName
        editingId = id
    }

    BackHandler(enabled = editingId != null) { editingId = null }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Eliminar perfil?") },
            text = { Text("Este perfil e o histórico associado serão eliminados.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(id)
                    deleteId = null
                }) { Text("Eliminar", color = Color(0xFFFF9999)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancelar") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Dark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (editingId != null) editingId = null else onBack() },
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text(
                if (editingId == null) "Perfis" else if (editingId == NEW) "Novo perfil" else "Alterar nome",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (editingId == null) {
                IconButton(onClick = { openEditor(NEW, "") }) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar perfil", tint = Green)
                }
            }
        }

        if (editingId != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    "Escolhe o nome que queres utilizar neste dispositivo.",
                    color = Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(24) },
                    label = { Text("Nome do perfil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { saveProfile() },
                    enabled = nameInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Guardar") }
                OutlinedButton(
                    onClick = { editingId = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancelar") }
            }
        } else {
            Text(
                "Escolhe o perfil para continuar a ver os teus favoritos e conteúdos.",
                color = Secondary,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val active = profile.id == activeId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Green,
                                )
                                Text(
                                    profile.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    maxLines = 2,
                                )
                            }
                            if (active) {
                                Text(
                                    "Perfil ativo",
                                    color = Green,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!active) {
                                    TextButton(onClick = { viewModel.select(profile.id) }) {
                                        Text("Utilizar")
                                    }
                                }
                                TextButton(onClick = { openEditor(profile.id, profile.name) }) {
                                    Text("Renomear")
                                }
                                if (profile.id != 1L) {
                                    TextButton(onClick = { deleteId = profile.id }) {
                                        Text("Eliminar", color = Color(0xFFFFB3B3))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { openEditor(NEW, "") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp, top = 6.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" Adicionar perfil")
                    }
                }
            }
        }
    }
}
