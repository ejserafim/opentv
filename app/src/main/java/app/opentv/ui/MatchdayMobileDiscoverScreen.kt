/*
 * Matchday Play — Início cinematográfico no telemóvel.
 * Catálogo e imagens vêm da biblioteca real configurada pelo cliente.
 * OpenTV GPL-3.0-or-later.
 */
package app.opentv.ui

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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.parser.displayTitle
import app.opentv.ui.vod.ContinueWatchingRow
import app.opentv.ui.vod.MoviePosterRow
import app.opentv.ui.vod.SeriesPosterRow
import coil.compose.AsyncImage

private val Ink = Color(0xFF06121D)
private val Green = Color(0xFF36F34B)
private val Muted = Color(0xFFB6C8C4)

@Composable
fun MatchdayMobileDiscoverScreen(
    hasSources: Boolean,
    onMatchday: () -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    viewModel: VodViewModel = viewModel(),
) {
    val resume by viewModel.continueWatching.collectAsState()
    val recommended by viewModel.recommendedMovies.collectAsState()
    val recentMovies by viewModel.recentlyAddedMovies.collectAsState()
    val recentSeries by viewModel.recentlyAddedSeries.collectAsState()
    val movieGenres by viewModel.movieGenreRows.collectAsState()
    val seriesGenres by viewModel.seriesGenreRows.collectAsState()
    val loading by viewModel.vodLoading.collectAsState()

    LaunchedEffect(hasSources) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }

    val film = recommended.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: recommended.firstOrNull() ?: recentMovies.firstOrNull()
    val show = if (film == null) recentSeries.firstOrNull() else null
    val featuredTitle = film?.displayTitle ?: show?.displayTitle
    val featuredImage = film?.backdropUrl ?: film?.posterUrl ?: show?.backdropUrl ?: show?.posterUrl

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "featured") {
            Box(
                Modifier.fillMaxWidth().height(292.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF14452A), Ink))),
            ) {
                if (!featuredImage.isNullOrBlank()) {
                    AsyncImage(
                        model = featuredImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color(0x4406111B), Color(0xA106121D), Ink),
                        ),
                    ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                ) {
                    Text(
                        "MATCHDAY PLAY ORIGINAL",
                        color = Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        featuredTitle ?: "O TEU ENTRETENIMENTO COMEÇA AQUI",
                        color = Color.White,
                        fontSize = 25.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (featuredTitle == null) "FUTEBOL  ·  FILMES  ·  SÉRIES"
                        else if (film != null) "FILME EM DESTAQUE"
                        else "SÉRIE EM DESTAQUE",
                        color = Muted,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (film != null) onOpenMovie(film)
                                else if (show != null) onOpenSeries(show)
                                else onLive()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(if (featuredTitle == null) "Ver TV" else "Ver detalhes", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onLive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xDB244233), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("TV em direto")
                        }
                    }
                }
            }
        }
        item(key = "matchday-card") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF114C2A), Color(0xFF102A32))))
                    .clickable(onClick = onMatchday)
                    .padding(horizontal = 17.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MATCHDAY MODE",
                        color = Green,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Agenda de futebol · Canais em direto",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Abrir Matchday Mode",
                    tint = Color.White,
                )
            }
        }
        item(key = "shortcuts") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickLink("TV", Icons.Filled.LiveTv, Modifier.weight(1f), onLive)
                QuickLink("Filmes", Icons.Filled.Movie, Modifier.weight(1f), onMovies)
                QuickLink("Séries", Icons.Filled.Tv, Modifier.weight(1f), onSeries)
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            ContinueWatchingRow(resume.take(25), onResume)
        }
        if (recommended.isNotEmpty()) item(key = "recommended") {
            MoviePosterRow("Escolhas para ti", recommended.take(30), onOpenMovie)
        }
        if (recentSeries.isNotEmpty()) item(key = "series") {
            SeriesPosterRow("Séries em destaque", recentSeries.take(30), onOpenSeries)
        }
        if (recentMovies.isNotEmpty()) item(key = "movies") {
            MoviePosterRow("Filmes recentes", recentMovies.take(30), onOpenMovie)
        }
        items(movieGenres.take(3), key = { "movie:" + it.genre }) { group ->
            if (group.items.isNotEmpty()) MoviePosterRow(group.genre, group.items.take(30), onOpenMovie)
        }
        items(seriesGenres.take(2), key = { "series:" + it.genre }) { group ->
            if (group.items.isNotEmpty()) SeriesPosterRow(group.genre, group.items.take(30), onOpenSeries)
        }
        if (loading && recommended.isEmpty() && recentMovies.isEmpty() && recentSeries.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun QuickLink(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF162D31))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
