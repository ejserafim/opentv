/*
 * Matchday Play — Matchday Mode, sobre OpenTV (GPL-3.0-or-later).
 * Eventos e horários vêm EXCLUSIVAMENTE do EPG real; nada é inventado.
 */
package app.opentv.ui

import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.StreamKind
import app.opentv.data.model.shownName
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.player.PlaybackQueue
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val MatchGreen = Color(0xFF36F34B)
private val MatchNight = Color(0xFF06121D)
private val MatchCard = Color(0xFF122C33)
private val MatchMuted = Color(0xFFB6C9C5)
private val sportsPattern = Regex(
    "futebol|football|soccer|sports?|desporto|bein|dazn|eleven|espn|liga|uefa|champions|eurosport",
    RegexOption.IGNORE_CASE,
)
private val footballPattern = Regex(
    "futebol|football|soccer|liga portugal|champions league|liga dos campe[oõ]es|" +
        "europa league|conference league|premier league|la liga|bundesliga|" +
        "serie a|world cup|euro 20|ta[cç]a de portugal|superta[cç]a|" +
        "copa libertadores|\\s+vs\\.?\\s+|\\s+x\\s+",
    RegexOption.IGNORE_CASE,
)
data class MatchCategory(val key: String, val label: String, val ids: List<String>)
data class MatchChannel(
    val channel: Channel,
    val variants: List<Channel>,
    val now: Programme?,
    val next: Programme?,
)
data class MatchEvent(val channel: Channel, val item: Programme, val live: Boolean)
data class MatchSnapshot(
    val channels: List<MatchChannel> = emptyList(),
    val events: List<MatchEvent> = emptyList(),
    val ready: Boolean = false,
)

class MatchdayModeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)
    private val settings = AppSettings.get(app)
    val categoryKey = MutableStateFlow<String?>(null)
    val favouritesOnly = MutableStateFlow(false)
    private val fixtureSource = MatchdayFixtureSource(graph.httpClient)
    val upcomingFixtures = MutableStateFlow<List<MatchdayFixture>>(emptyList())
    val fixturesLoading = MutableStateFlow(false)
    val fixturesError = MutableStateFlow<String?>(null)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())

    init {
        refreshFixtures()
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                nowFlow.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshFixtures() {
        if (fixturesLoading.value) return
        viewModelScope.launch {
            fixturesLoading.value = true
            fixturesError.value = null
            try {
                upcomingFixtures.value = fixtureSource.upcoming()
            } catch (_: Exception) {
                fixturesError.value = "Não foi possível consultar a agenda externa."
            } finally {
                fixturesLoading.value = false
            }
        }
    }

    val categories: StateFlow<List<MatchCategory>> = combine(
        graph.catalogRepository.observeCategories(StreamKind.LIVE),
        settings.hiddenCategories,
        settings.hiddenUnlocked,
    ) { all, hidden, unlocked ->
        val grouped = LinkedHashMap<String, Pair<String, MutableList<String>>>()
        for (cat in all) {
            if (!sportsPattern.containsMatchIn(cat.name)) continue
            val clean = ChannelNameNormalizer.normalize(cat.name)
            val key = clean.groupKey.ifBlank { cat.id }
            if (!unlocked && key in hidden) continue
            val bucket = grouped.getOrPut(key) { clean.baseName to mutableListOf() }
            if (cat.id !in bucket.second) bucket.second.add(cat.id)
        }
        grouped.map { (key, v) -> MatchCategory(key, v.first, v.second) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }.take(35)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val channels = combine(categories, categoryKey) { groups, key ->
        (if (key == null) groups else groups.filter { it.key == key })
            .flatMap { it.ids }.distinct().take(100)
    }.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList())
        else graph.catalogRepository.observeChannelsIn(ids)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val guide = nowFlow.flatMapLatest { now ->
        graph.epgRepository.observeWindow(now - 2L * 60 * 60 * 1000, now + 10L * 60 * 60 * 1000)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    val snapshot: StateFlow<MatchSnapshot> =
        combine(channels, guide, nowFlow, favouritesOnly) { all, programmes, now, onlyFav ->
            val programmesById = programmes.groupBy { it.epgChannelId }
            val groups = LinkedHashMap<String, MutableList<Channel>>()
            all.filterNot { it.hidden }.forEach { ch ->
                groups.getOrPut(ch.groupKey.ifBlank { "channel-" + ch.id }) { mutableListOf() }.add(ch)
            }
            val items = groups.values.mapNotNull { variants ->
                val sorted = variants.sortedByDescending { it.qualityRank }
                val first = sorted.firstOrNull() ?: return@mapNotNull null
                if (onlyFav && sorted.none { it.favourite }) return@mapNotNull null
                val epg = sorted.asSequence().flatMap { ch ->
                    sequenceOf(ch.epgOverrideId, ch.epgChannelId, ch.matchedEpgId)
                        .filterNotNull().distinct()
                }.mapNotNull { id -> programmesById[id]?.takeIf { it.isNotEmpty() } }
                    .firstOrNull().orEmpty().sortedBy { it.startUtcMillis }
                MatchChannel(
                    channel = first,
                    variants = sorted,
                    now = epg.firstOrNull { it.startUtcMillis <= now && it.endUtcMillis > now },
                    next = epg.firstOrNull { it.startUtcMillis > now },
                )
            }.sortedWith(compareByDescending<MatchChannel> { it.channel.favourite }.thenBy { it.channel.shownName })
            val events = items.flatMap { row ->
                listOfNotNull(row.now, row.next).filter { footballPattern.containsMatchIn(it.title) }
                    .map { event ->
                        MatchEvent(row.channel, event, event.startUtcMillis <= now && event.endUtcMillis > now)
                    }
            }.distinctBy { it.channel.id to it.item.id }
                .sortedWith(compareByDescending<MatchEvent> { it.live }.thenBy { it.item.startUtcMillis })
                .take(40)
            MatchSnapshot(items.take(120), events, ready = true)
        }.flowOn(Dispatchers.Default).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchSnapshot(),
        )

    fun choose(key: String?) { categoryKey.value = key }
    fun toggleFilter() { favouritesOnly.value = !favouritesOnly.value }
    fun toggleStar(item: MatchChannel) {
        viewModelScope.launch {
            val wantFavourite = !item.channel.favourite
            item.variants.forEach { graph.catalogRepository.setChannelFavourite(it.id, wantFavourite) }
        }
    }
}

@Composable
fun MatchdayModeScreen(
    onBack: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    model: MatchdayModeViewModel = viewModel(),
) {
    val cats by model.categories.collectAsState()
    val choice by model.categoryKey.collectAsState()
    val onlyFavourites by model.favouritesOnly.collectAsState()
    val data by model.snapshot.collectAsState()
    val upcoming by model.upcomingFixtures.collectAsState()
    val fixturesLoading by model.fixturesLoading.collectAsState()
    val fixturesError by model.fixturesError.collectAsState()
    val context = LocalContext.current
    var selectedFixture by remember { mutableStateOf<MatchdayFixture?>(null) }
    var fixtureRevision by remember { mutableIntStateOf(0) }
    BackHandler(enabled = selectedFixture == null) { onBack() }

    fun play(channel: Channel) {
        PlaybackQueue.items = data.channels.map {
            PlaybackQueue.Item(it.channel.id, it.channel.shownName, it.channel.logoUrl, it.channel.number)
        }
        onPlayChannel(channel)
    }

    selectedFixture?.let { fixture ->
        MatchdayFixtureDialog(
            fixture = fixture,
            channels = data.channels.map { it.channel },
            onDismiss = { selectedFixture = null },
            onChanged = { fixtureRevision++ },
            onPlay = { channel -> play(channel) },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MatchNight),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E432D), MatchNight)))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                    Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = MatchGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("MATCHDAY MODE", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "O futebol começa aqui.",
                    color = MatchGreen,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp),
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "Agenda da Liga Portugal, canais desportivos e programação em direto.",
                    color = MatchMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item {
            Column {
                Text(
                    "Explorar desporto",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = choice == null,
                            onClick = { model.choose(null) },
                            label = { Text("Todos") },
                        )
                    }
                    items(cats, key = { it.key }) { cat ->
                        FilterChip(
                            selected = choice == cat.key,
                            onClick = { model.choose(cat.key) },
                            label = { Text(cat.label, maxLines = 1) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Apenas favoritos", color = MatchMuted, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = onlyFavourites,
                        onClick = model::toggleFilter,
                        label = { Text(if (onlyFavourites) "Ativo" else "Todos") },
                    )
                }
            }
        }

        item(key = "fixtures-heading") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Próximos jogos · Liga Portugal",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = model::refreshFixtures, enabled = !fixturesLoading) {
                        Text("Atualizar", color = MatchGreen)
                    }
                }
                Text(
                    "Calendário independente da programação IPTV · próximos 21 dias",
                    color = MatchMuted,
                    fontSize = 12.sp,
                )
            }
        }
        if (upcoming.isEmpty()) {
            item(key = "fixtures-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp)).background(MatchCard).padding(16.dp),
                ) {
                    if (fixturesLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MatchGreen,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("A carregar próximos jogos…", color = Color.White)
                        }
                    } else {
                        Text(
                            fixturesError ?: "Sem partidas disponíveis para os próximos dias.",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A agenda do fornecedor IPTV continua disponível abaixo.",
                            color = MatchMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            items(upcoming, key = { "fixture-" + it.date + it.home + it.away }) { fixture ->
                val channelName = remember(fixture, fixtureRevision) {
                    MatchdayFixtureAlerts.selectedChannelName(context, fixture)
                }
                val hasNotice = remember(fixture, fixtureRevision) {
                    MatchdayFixtureAlerts.hasReminder(context, fixture)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MatchCard)
                        .clickable { selectedFixture = fixture }
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            fixture.date.substring(8, 10) + "/" + fixture.date.substring(5, 7),
                            color = MatchGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                        )
                        Text(
                            fixture.time ?: "A confirmar",
                            color = MatchMuted,
                            fontSize = if (fixture.time == null) 10.sp else 12.sp,
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            fixture.home,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                        Text(
                            "vs",
                            color = MatchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            fixture.away,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            channelName?.let { "Escolhido: " + it }
                                ?: "Escolher canal · Ativar aviso",
                            color = if (hasNotice || channelName != null) MatchGreen else MatchMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                        )
                    }
                    if (hasNotice) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = "Aviso ativo",
                            tint = MatchGreen,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Abrir jogo", tint = MatchGreen)
                }
            }
        }
        item(key = "fixtures-source") {
            Text(
                "Fonte: OpenFootball (dados comunitários). Horários sujeitos a confirmação. " +
                    "A presença de um jogo não significa que esteja disponível nos canais IPTV.",
                color = MatchMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 17.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Jogos na programação dos canais", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Informação EPG do teu fornecedor IPTV", color = MatchMuted, fontSize = 12.sp)
            }
        }
        if (data.events.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp)).background(MatchCard).padding(16.dp),
                ) {
                    Text(
                        if (!data.ready) "A consultar o guia…" else "Nenhum jogo identificado neste momento.",
                        color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Os títulos e horários dependem da informação do fornecedor. Explora os canais abaixo.",
                        color = MatchMuted, fontSize = 13.sp,
                    )
                }
            }
        } else {
            items(data.events, key = { "event-" + it.channel.id + "-" + it.item.id }) { event ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp)).background(MatchCard)
                        .clickable { play(event.channel) }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (event.live) "DIRETO" else SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(event.item.startUtcMillis)),
                            color = if (event.live) MatchGreen else Color.White,
                            fontWeight = FontWeight.Black, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(event.channel.shownName, color = MatchMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Abrir canal", tint = MatchGreen)
                }
            }
        }
        item {
            Text(
                "Canais desportivos",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (data.channels.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (!data.ready) CircularProgressIndicator(color = MatchGreen)
                    else Text("Não foram encontrados canais nesta seleção.", color = MatchMuted)
                }
            }
        } else {
            items(data.channels, key = { it.channel.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(13.dp)).background(MatchCard)
                        .clickable { play(item.channel) }
                        .padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.channel.logoUrl.isNullOrBlank()) {
                        Icon(
                            Icons.Filled.SportsSoccer, contentDescription = null,
                            tint = MatchGreen, modifier = Modifier.size(36.dp),
                        )
                    } else {
                        AsyncImage(
                            model = item.channel.logoUrl, contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.channel.shownName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                        val info = item.now?.let { "Agora: " + it.title }
                            ?: item.next?.let { "A seguir: " + it.title } ?: "Ver canal"
                        Text(
                            info, color = MatchMuted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { model.toggleStar(item) }) {
                        Icon(
                            if (item.channel.favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Alternar favorito", tint = MatchGreen,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MatchMuted)
                }
            }
        }
    }
}
