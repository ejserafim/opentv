/*
 * Matchday Play — Matchday Mode, sobre OpenTV (GPL-3.0-or-later).
 * Agenda externa de futebol e EPG são apresentados separadamente; nada é inventado.
 */
package app.opentv.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.NotificationsActive
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
import kotlinx.coroutines.FlowPreview
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
    // O filtro dos jogos é independente do filtro dos canais e fica guardado.
    private val clubFilterPrefs = app.getSharedPreferences("matchday_club_preferences", 0)
    val onlyMyClubGames = MutableStateFlow(clubFilterPrefs.getBoolean("only_my_club_games", false))
    val favouritesOnly = MutableStateFlow(false)
    private val pickerQuery = MutableStateFlow("")
    private val fixtureSource = MatchdayFixtureSource(graph.httpClient)
    private val clubPrefs = app.getSharedPreferences("matchday_club_preferences", 0)
    val favoriteClub = MutableStateFlow(clubPrefs.getString("favorite_club", null))
    val autoClubAlerts = MutableStateFlow(clubPrefs.getBoolean("automatic_alerts", false))
    val clubAlertsCount = MutableStateFlow(0)
    // Local snapshot is displayed immediately while the network source updates.
    val upcomingFixtures = MutableStateFlow(MatchdayFixtureCache.read(app))
    val calendarLastUpdated = MutableStateFlow(MatchdayFixtureCache.lastUpdatedMillis(app))
    val fixturesLoading = MutableStateFlow(false)
    val fixturesError = MutableStateFlow<String?>(null)
    val usingOfflineLeague = MutableStateFlow(false)
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
                val fetched = fixtureSource.upcoming()
                val leagueLoaded = fixtureSource.lastLeagueLoadSucceeded
                val cache = MatchdayFixtureCache.read(getApplication())
                upcomingFixtures.value = matchdayDisplayMerge(
                    fetched = fetched,
                    cached = cache,
                    leagueLoaded = leagueLoaded,
                )
                usingOfflineLeague.value = !leagueLoaded && cache.any {
                    it.competition == "Liga Portugal"
                }
                if (leagueLoaded) {
                    MatchdayFixtureCache.save(getApplication(), fetched)
                    calendarLastUpdated.value = MatchdayFixtureCache.lastUpdatedMillis(getApplication())
                }
                syncClubAlerts()
            } catch (_: Exception) {
                usingOfflineLeague.value = upcomingFixtures.value.isNotEmpty()
                fixturesError.value =
                    if (upcomingFixtures.value.isEmpty())
                        "Não foi possível consultar o calendário. Verifica a ligação."
                    else "Sem ligação à agenda. A mostrar jogos guardados."
            } finally {
                fixturesLoading.value = false
            }
        }
    }

    /** Guardados no dispositivo; clubes disponíveis nesta fase: Primeira Liga. */
    fun chooseFavoriteClub(club: String?) {
        if (favoriteClub.value == club) return
        favoriteClub.value = club
        clubPrefs.edit().putString("favorite_club", club).apply()
        if (club == null) {
            autoClubAlerts.value = false
            clubPrefs.edit().putBoolean("automatic_alerts", false).apply()
        }
        MatchdayFixtureAlerts.cancelAutomaticReminders(getApplication())
        MatchdayFixtureAlerts.resetAutomaticSkips(getApplication())
        MatchdayFixtureSyncWorker.configure(getApplication())
        syncClubAlerts()
    }

    fun enableAutomaticClubAlerts(enabled: Boolean) {
        if (enabled && favoriteClub.value == null) return
        if (enabled && !autoClubAlerts.value) {
            MatchdayFixtureAlerts.resetAutomaticSkips(getApplication())
        }
        autoClubAlerts.value = enabled
        clubPrefs.edit().putBoolean("automatic_alerts", enabled).apply()
        MatchdayFixtureSyncWorker.configure(getApplication())
        syncClubAlerts()
    }

    private fun syncClubAlerts() {
        val club = favoriteClub.value.takeIf { autoClubAlerts.value }
        val games = upcomingFixtures.value
        viewModelScope.launch(Dispatchers.IO) {
            clubAlertsCount.value = MatchdayFixtureAlerts.synchronizeAutomatic(
                getApplication(), games, club,
                leagueLoaded = fixtureSource.lastLeagueLoadSucceeded,
            )
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

    /**
     * Pesquisa qualquer canal do catálogo, mesmo quando o fornecedor agrupa os canais
     * desportivos em "PORTUGAL", "EXCLUSIVOS" etc. Nunca expõe categorias bloqueadas.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val pickerResults: StateFlow<List<Channel>> = combine(
        pickerQuery.debounce(250).flatMapLatest { q ->
            if (q.trim().length < 2) flowOf(emptyList())
            else graph.catalogRepository.searchChannels(q.trim())
        },
        graph.catalogRepository.observeCategories(StreamKind.LIVE),
        settings.hiddenCategories,
        settings.hiddenUnlocked,
    ) { found, allCategories, hidden, unlocked ->
        val forbidden = if (unlocked) emptySet() else allCategories.filter { cat ->
            val normalized = ChannelNameNormalizer.normalize(cat.name)
            normalized.groupKey.ifBlank { cat.id } in hidden
        }.map { it.id }.toSet()
        found.filterNot { it.hidden || it.categoryId in forbidden }
            .distinctBy { it.groupKey.ifBlank { "channel-" + it.id } }
            .take(50)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    fun searchPossibleChannels(text: String) { pickerQuery.value = text }

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
    fun toggleClubGamesFilter() {
        onlyMyClubGames.value = !onlyMyClubGames.value
        clubPrefs.edit().putBoolean("only_my_club_games", onlyMyClubGames.value).apply()
    }
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
    val onlyMyClubGames by model.onlyMyClubGames.collectAsState()
    val data by model.snapshot.collectAsState()
    val upcoming by model.upcomingFixtures.collectAsState()
    val fixturesLoading by model.fixturesLoading.collectAsState()
    val fixturesError by model.fixturesError.collectAsState()
    val usingOfflineLeague by model.usingOfflineLeague.collectAsState()
    val calendarLastUpdated by model.calendarLastUpdated.collectAsState()
    val favoriteClub by model.favoriteClub.collectAsState()
    val autoClubAlerts by model.autoClubAlerts.collectAsState()
    val clubAlertsCount by model.clubAlertsCount.collectAsState()
    val searchChannels by model.pickerResults.collectAsState()
    val context = LocalContext.current
    // Não altera os lembretes. Filtra apenas o que é mostrado no ecrã.
    val displayedFixtures = remember(upcoming, favoriteClub, onlyMyClubGames) {
        matchdayFixturesForClub(upcoming, favoriteClub, onlyMyClubGames)
    }
    val displayedEpgEvents = remember(data.events, favoriteClub, onlyMyClubGames) {
        if (onlyMyClubGames && !favoriteClub.isNullOrBlank()) {
            data.events.filter { matchdayProgrammeMentionsClub(it.item.title, favoriteClub!!) }
        } else data.events
    }
    var selectedFixture by remember { mutableStateOf<MatchdayFixture?>(null) }
    var fixtureRevision by remember { mutableIntStateOf(0) }
    var clubPickerOpen by remember { mutableStateOf(false) }
    val availableClubs = remember(upcoming) {
        // Só clubes portugueses na escolha inicial; equipas europeias são adversárias,
        // não novos clubes a seguir. O Benfica continua elegível sem o feed da Liga.
        val leagueClubs = upcoming.filter { it.competition == "Liga Portugal" }
            .flatMap { listOf(it.home, it.away) }
        val europeanPortuguese = if (upcoming.any {
                it.competition == "Liga Europa" && it.involvesClub("Benfica")
            }) listOf("Sport Lisboa e Benfica") else emptyList()
        (leagueClubs + europeanPortuguese).distinctBy(::matchdayTeamKey)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            model.enableAutomaticClubAlerts(true)
        } else {
            Toast.makeText(context, "Autoriza as notificações para receber avisos.", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleAutomaticAlerts(enabled: Boolean) {
        if (!enabled) {
            model.enableAutomaticClubAlerts(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.enableAutomaticClubAlerts(true)
        }
    }

    if (clubPickerOpen) {
        AlertDialog(
            onDismissRequest = { clubPickerOpen = false },
            title = { Text("Qual é o teu clube?") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(availableClubs, key = { it }) { club ->
                        TextButton(
                            onClick = {
                                model.chooseFavoriteClub(club)
                                clubPickerOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                club,
                                color = if (club == favoriteClub) MatchGreen else Color.White,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (favoriteClub != null) {
                        item {
                            TextButton(
                                onClick = {
                                    model.chooseFavoriteClub(null)
                                    clubPickerOpen = false
                                },
                            ) { Text("Deixar de seguir o clube", color = MatchMuted) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clubPickerOpen = false }) { Text("Fechar") }
            },
        )
    }

    BackHandler(enabled = selectedFixture == null && !clubPickerOpen) { onBack() }

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
            searchResults = searchChannels,
            onSearch = model::searchPossibleChannels,
            onDismiss = {
                model.searchPossibleChannels("")
                selectedFixture = null
            },
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
                    "Liga Portugal, Liga Europa e jogos em direto. Segue o teu clube em várias provas.",
                    color = MatchMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        item(key = "club-follow") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF164A32), Color(0xFF132A32)),
                        ),
                    ).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SportsSoccer,
                        contentDescription = null,
                        tint = MatchGreen,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "O MEU CLUBE",
                        color = MatchGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    favoriteClub ?: "Ainda não escolheste um clube.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                )
                OutlinedButton(
                    onClick = { clubPickerOpen = true },
                    enabled = availableClubs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (favoriteClub == null) "Escolher clube da Liga Portugal" else "Mudar clube")
                }
                if (availableClubs.isEmpty()) {
                    Text(
                        "Os clubes ficam disponíveis quando o calendário for carregado.",
                        color = MatchMuted,
                        fontSize = 12.sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Avisos automáticos",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "15 min antes dos jogos do teu clube",
                            color = MatchMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = autoClubAlerts,
                        onCheckedChange = ::toggleAutomaticAlerts,
                        enabled = favoriteClub != null,
                    )
                }
                if (autoClubAlerts) {
                    Text(
                        if (clubAlertsCount == 0)
                            "Sem jogos com hora confirmada para criar avisos."
                        else "$clubAlertsCount jogo(s) com aviso programado.",
                        color = MatchGreen,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Os avisos do teu clube são atualizados em segundo plano quando há ligação à " +
                        "internet. A Liga Europa ainda depende de partidas verificadas manualmente.",
                    color = MatchMuted,
                    fontSize = 11.sp,
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
            }
        }

        item(key = "fixtures-heading") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Próximos jogos · Todas as provas",
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
                    "Liga Portugal e jogos europeus verificados · próximos 21 dias",
                    color = MatchMuted,
                    fontSize = 12.sp,
                )
                if (calendarLastUpdated > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (usingOfflineLeague)
                            "Liga em cache · última atualização: " +
                                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    .format(Date(calendarLastUpdated))
                        else
                            "Agenda guardada · atualizada: " +
                                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    .format(Date(calendarLastUpdated)),
                        color = if (usingOfflineLeague) Color(0xFFF5CC75) else MatchMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Só jogos do meu clube",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (favoriteClub == null) "Escolhe primeiro um clube acima"
                            else if (onlyMyClubGames) favoriteClub!!
                            else "Mostrar todas as equipas",
                            color = MatchMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    FilterChip(
                        selected = onlyMyClubGames && favoriteClub != null,
                        onClick = model::toggleClubGamesFilter,
                        enabled = favoriteClub != null,
                        label = { Text(if (onlyMyClubGames && favoriteClub != null) "Ativo" else "Todos") },
                    )
                }
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
        } else if (displayedFixtures.isEmpty()) {
            item(key = "fixtures-filter-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MatchCard)
                        .padding(16.dp),
                ) {
                    Text(
                        "Não há jogos do teu clube nos próximos 21 dias.",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Podes voltar a 'Todos' para consultar a agenda completa.",
                        color = MatchMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            items(displayedFixtures, key = { "fixture-" + it.date + it.home + it.away }) { fixture ->
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
                            fixture.competition +
                                (fixture.round?.let { " · " + it } ?: ""),
                            color = MatchGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
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
                        if (!fixture.broadcastChannel.isNullOrBlank()) {
                            Text(
                                "Onde ver: " + fixture.broadcastChannel + " · fonte: " +
                                    (fixture.broadcastSource ?: "verificada"),
                                color = MatchGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                            )
                        }
                        Text(
                            channelName?.let { "Canal associado: " + it }
                                ?: "Associar canal · Ativar aviso",
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
                "Fontes: OpenFootball (Liga Portugal), Benfica/UEFA (Liga Europa). " +
                    "Os jogos europeus desta versão foram confirmados manualmente, não constituem " +
                    "uma agenda automática de todas as provas. Horários sujeitos a alterações. " +
                    "Ter uma emissora identificada não garante que o canal esteja na tua lista.",
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
        if (displayedEpgEvents.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp)).background(MatchCard).padding(16.dp),
                ) {
                    Text(
                        if (!data.ready) "A consultar o guia…"
                        else if (onlyMyClubGames && favoriteClub != null)
                            "Sem jogos do teu clube identificados no guia."
                        else "Nenhum jogo identificado neste momento.",
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
            items(displayedEpgEvents, key = { "event-" + it.channel.id + "-" + it.item.id }) { event ->
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
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Canais desportivos",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Só canais favoritos",
                        color = MatchMuted,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                    )
                    FilterChip(
                        selected = onlyFavourites,
                        onClick = model::toggleFilter,
                        label = { Text(if (onlyFavourites) "Ativo" else "Todos") },
                    )
                }
            }
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
