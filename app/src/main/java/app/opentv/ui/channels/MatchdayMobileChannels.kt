/*
 * Matchday Play — interface para escolha de canais no Android móvel.
 * Derivado de OpenTV sob GPL-3.0-or-later.
 */
package app.opentv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.player.PlaybackQueue
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage

private val Dark = Color(0xFF06121D)
private val Surface = Color(0xFF10232C)
private val Green = Color(0xFF36F34B)
private val Secondary = Color(0xFFB6CBC6)

@Composable
fun MatchdayMobileChannels(
    viewModel: ChannelsViewModel,
    hasSources: Boolean,
    isSyncing: Boolean,
    channelsPresent: Boolean?,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
) {
    val categories by viewModel.visibleCategoryGroups.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favouritesOnly by viewModel.favouritesOnly.collectAsState()
    var showingChannels by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showingChannels) { showingChannels = false }

    val currentLabel = when {
        favouritesOnly -> "Favoritos"
        selectedCategory == null -> "Todos os canais"
        else -> categories.firstOrNull { it.key == selectedCategory }?.label ?: "Canais"
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showingChannels) {
                IconButton(onClick = { showingChannels = false }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar às categorias", tint = Color.White)
                }
            }
            Text(
                if (showingChannels) currentLabel else "TV EM DIRETO",
                modifier = Modifier.weight(1f).padding(start = if (showingChannels) 2.dp else 14.dp),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar canais", tint = Green)
            }
        }
        if (!showingChannels) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "favorites") {
                    CategoryItem("★  Favoritos", onClick = {
                        viewModel.selectFavourites()
                        showingChannels = true
                    })
                }
                item(key = "all") {
                    CategoryItem("Todos os canais", onClick = {
                        viewModel.selectCategory(null)
                        showingChannels = true
                    })
                }
                items(categories, key = { it.key }) { category ->
                    CategoryItem(category.label, onClick = {
                        viewModel.selectCategory(category.key)
                        showingChannels = true
                    })
                }
                if (categories.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            if (isSyncing || channelsPresent == null) {
                                CircularProgressIndicator(color = Green)
                            } else {
                                Text(
                                    if (hasSources) "Sem categorias disponíveis. Tenta atualizar."
                                    else "Nenhuma conta configurada.",
                                    color = Secondary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (isSyncing || channelsPresent == null || (channelsPresent == true && !favouritesOnly)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Green)
                            Spacer(Modifier.height(14.dp))
                            Text("A carregar os canais…", color = Secondary)
                        }
                    } else {
                        Text(
                            if (favouritesOnly) "Ainda não tens canais favoritos."
                            else "Não existem canais nesta categoria.",
                            color = Secondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.primary.id }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Surface)
                                .clickable {
                                    PlaybackQueue.items = rows.map {
                                        PlaybackQueue.Item(
                                            it.primary.id,
                                            it.primary.shownName,
                                            it.primary.logoUrl,
                                            it.primary.number,
                                        )
                                    }
                                    onPlayChannel(row.primary)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!row.primary.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = row.primary.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Green,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.primary.shownName,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 2,
                                )
                                row.now?.title?.let { programme ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        programme,
                                        color = Secondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Reproduzir canal",
                                tint = Green,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Green)
    }
}
