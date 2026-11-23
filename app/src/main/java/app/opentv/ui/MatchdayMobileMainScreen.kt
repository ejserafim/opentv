/*
 * Matchday Play — navegação móvel para telefones e tablets Android.
 * Derivado de OpenTV sob GPL-3.0-or-later.
 */
package app.opentv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.core.AppSettings
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Recording
import app.opentv.ui.channels.HomeScreen
import app.opentv.ui.recordings.RecordingsScreen
import app.opentv.ui.vod.MoviesScreen
import app.opentv.ui.vod.SeriesScreen

private val Accent = Color(0xFF36F34B)
private val Background = Color(0xFF06121D)

@Composable
fun MatchdayMobileMainScreen(
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onPlayCatchup: (mediaKey: String, url: String, title: String, ua: String) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val liveEnabled by settings.liveEnabled.collectAsState()
    val moviesEnabled by settings.moviesEnabled.collectAsState()
    val seriesEnabled by settings.seriesEnabled.collectAsState()
    val tabs = remember(liveEnabled, moviesEnabled, seriesEnabled) {
        buildList {
            if (liveEnabled) add(Tab.LIVE)
            if (moviesEnabled) add(Tab.MOVIES)
            if (seriesEnabled) add(Tab.SHOWS)
            add(Tab.RECORDINGS)
        }
    }
    // Preservar o separador: ao sair de um filme, volta à secção Filmes e não à TV.
    var currentName by rememberSaveable { mutableStateOf(tabs.first().name) }
    val current = tabs.firstOrNull { it.name == currentName } ?: tabs.first()
    LaunchedEffect(tabs) {
        if (tabs.none { it.name == currentName }) currentName = tabs.first().name
    }
    BackHandler(enabled = current != tabs.first()) { currentName = tabs.first().name }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF081B20))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MATCHDAY",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
            Text(
                " PLAY",
                color = Accent,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Pesquisar", tint = Color.White)
            }
            IconButton(onClick = onOpenProfiles) {
                Icon(Icons.Filled.Person, contentDescription = "Perfis", tint = Color.White)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Definições", tint = Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                Tab.LIVE -> HomeScreen(
                    isTelevision = false,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                    onPlayChannel = onPlayChannel,
                    onAddSource = onAddSource,
                    onRefresh = onRefresh,
                    onPlayCatchup = onPlayCatchup,
                )
                Tab.MOVIES -> MoviesScreen(
                    onOpenMovie = onOpenMovie,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                Tab.SHOWS -> SeriesScreen(
                    onOpenSeries = onOpenSeries,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                Tab.RECORDINGS -> RecordingsScreen(onPlay = onPlayRecording)
            }
        }
        NavigationBar(
            containerColor = Color(0xFF0D222B),
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = current == tab,
                    onClick = { currentName = tab.name },
                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF07140F),
                        selectedTextColor = Accent,
                        indicatorColor = Accent,
                        unselectedIconColor = Color(0xFFB0C4C1),
                        unselectedTextColor = Color(0xFFB0C4C1),
                    ),
                )
            }
        }
    }
}
