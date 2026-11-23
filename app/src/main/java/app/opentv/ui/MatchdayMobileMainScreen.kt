/*
 * Matchday Play — navegação móvel com página Início.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.FiberManualRecord
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private enum class MobileTab(val label: String) {
    START("Início"),
    LIVE("TV"),
    MOVIES("Filmes"),
    SHOWS("Séries"),
    RECORDINGS("Gravações"),
}

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
    val settings = remember { AppSettings.get(androidx.compose.ui.platform.LocalContext.current) }
    val liveEnabled by settings.liveEnabled.collectAsState()
    val moviesEnabled by settings.moviesEnabled.collectAsState()
    val seriesEnabled by settings.seriesEnabled.collectAsState()
    val tabs = remember(liveEnabled, moviesEnabled, seriesEnabled) {
        buildList {
            add(MobileTab.START)
            if (liveEnabled) add(MobileTab.LIVE)
            if (moviesEnabled) add(MobileTab.MOVIES)
            if (seriesEnabled) add(MobileTab.SHOWS)
            add(MobileTab.RECORDINGS)
        }
    }
    var currentName by rememberSaveable { mutableStateOf(MobileTab.START.name) }
    val current = tabs.firstOrNull { it.name == currentName } ?: MobileTab.START
    LaunchedEffect(tabs) {
        if (tabs.none { it.name == currentName }) currentName = MobileTab.START.name
    }
    BackHandler(enabled = current != MobileTab.START) { currentName = MobileTab.START.name }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF081B20))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MATCHDAY", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(" PLAY", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
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
                MobileTab.START -> MatchdayMobileDiscoverScreen(
                    hasSources = hasSources,
                    onLive = { if (liveEnabled) currentName = MobileTab.LIVE.name },
                    onMovies = { if (moviesEnabled) currentName = MobileTab.MOVIES.name },
                    onSeries = { if (seriesEnabled) currentName = MobileTab.SHOWS.name },
                    onOpenMovie = onOpenMovie,
                    onOpenSeries = onOpenSeries,
                    onResume = onResume,
                )
                MobileTab.LIVE -> HomeScreen(
                    isTelevision = false,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                    onPlayChannel = onPlayChannel,
                    onAddSource = onAddSource,
                    onRefresh = onRefresh,
                    onPlayCatchup = onPlayCatchup,
                )
                MobileTab.MOVIES -> MoviesScreen(
                    onOpenMovie = onOpenMovie,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                MobileTab.SHOWS -> SeriesScreen(
                    onOpenSeries = onOpenSeries,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    hasSources = hasSources,
                    isSyncing = isSyncing,
                )
                MobileTab.RECORDINGS -> RecordingsScreen(onPlay = onPlayRecording)
            }
        }
        NavigationBar(
            containerColor = Color(0xFF0D222B),
            tonalElevation = 0.dp,
        ) {
            tabs.forEach { tab ->
                val icon = when (tab) {
                    MobileTab.START -> Icons.Filled.Home
                    MobileTab.LIVE -> Icons.Filled.LiveTv
                    MobileTab.MOVIES -> Icons.Filled.Movie
                    MobileTab.SHOWS -> Icons.Filled.Tv
                    MobileTab.RECORDINGS -> Icons.Filled.FiberManualRecord
                }
                NavigationBarItem(
                    selected = current == tab,
                    onClick = { currentName = tab.name },
                    icon = { Icon(icon, contentDescription = tab.label) },
                    label = { Text(tab.label, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Background,
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
