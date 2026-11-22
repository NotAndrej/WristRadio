package com.notandrej.wristradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.notandrej.wristradio.data.Directory
import com.notandrej.wristradio.data.Favourites
import com.notandrej.wristradio.data.RadioBrowser
import com.notandrej.wristradio.data.Station
import com.notandrej.wristradio.playback.PlaybackConnection
import com.notandrej.wristradio.playback.PlaybackState

/** Where the app is. Plain state, no navigation library: there are six screens. */
sealed interface Route {
    object Home : Route
    object Search : Route
    object Favourites : Route
    object Player : Route

    data class Stations(
        val title: String,
        val source: StationSource,
        val back: Route = Home,
    ) : Route

    data class Categories(val kind: Kind) : Route {
        enum class Kind { GENRE, COUNTRY }
    }
}

/** What a station list should be fetched from. */
sealed interface StationSource {
    data class Query(val query: String) : StationSource
    data class Tag(val tag: String) : StationSource
    data class Country(val code: String) : StationSource
    object Popular : StationSource
}

@Composable
fun RadioApp() {
    val playback by PlaybackConnection.state.collectAsState()
    var route: Route by remember { mutableStateOf<Route>(Route.Home) }

    when (val current = route) {
        Route.Home -> HomeScreen(playback) { route = it }

        Route.Search -> SearchScreen(
            onBack = { route = Route.Home },
            onSearch = { query ->
                route = Route.Stations("\u201C$query\u201D", StationSource.Query(query), Route.Search)
            },
        )

        Route.Favourites -> StationListScreen(
            title = "Favourites",
            stations = null,
            state = playback,
            onBack = { route = Route.Home },
            onPlay = { PlaybackConnection.play(it); route = Route.Player },
        )

        Route.Player -> PlayerScreen(playback) { route = Route.Home }

        is Route.Categories -> CategoriesScreen(
            kind = current.kind,
            onBack = { route = Route.Home },
            onPick = { title, source ->
                route = Route.Stations(title, source, Route.Categories(current.kind))
            },
        )

        is Route.Stations -> StationsScreen(
            route = current,
            state = playback,
            onBack = { route = current.back },
            onPlay = { PlaybackConnection.play(it); route = Route.Player },
        )
    }
}

// ---- home ------------------------------------------------------------------

@Composable
private fun HomeScreen(state: PlaybackState, go: (Route) -> Unit) {
    val favourites by Favourites.items.collectAsState()
    val listState = rememberScalingLazyListState()

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(0) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WristRadio",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = state.station?.let { "Now: ${it.name}" } ?: "FOSS internet radio",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.station != null) {
                item(1) {
                    MenuChip(
                        label = if (state.playing) "Now playing" else "Resume playing",
                        secondary = state.station?.name.orEmpty(),
                        icon = Icons.Filled.GraphicEq,
                            onClick = { go(Route.Player) },
                    )
                }
            }

            item(2) {
                MenuChip("Search", "Find a station by name", Icons.Filled.Search) { go(Route.Search) }
            }
            item(3) {
                MenuChip(
                    label = "Favourites",
                    secondary = favourites.size.let { if (it == 1) "1 saved station" else "$it saved stations" },
                    icon = Icons.Filled.Star,
                ) { go(Route.Favourites) }
            }
            item(4) {
                MenuChip("Popular now", "Most-listened stations", Icons.Filled.Whatshot) {
                    go(Route.Stations("Popular now", StationSource.Popular, Route.Home))
                }
            }
            item(5) {
                MenuChip("Genres", "Browse ${Directory.genres.size} tags", Icons.Filled.Category) {
                    go(Route.Categories(Route.Categories.Kind.GENRE))
                }
            }
            item(6) {
                MenuChip("Countries", "Browse ${Directory.countries.size} countries", Icons.Filled.Public) {
                    go(Route.Categories(Route.Categories.Kind.COUNTRY))
                }
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ---- search ----------------------------------------------------------------

@Composable
private fun SearchScreen(onBack: () -> Unit, onSearch: (String) -> Unit) {
    BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    val listState = rememberScalingLazyListState()

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                BackChip(label = "Search", onBack = onBack)
            }
            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSubmit = { if (query.isNotBlank()) onSearch(query.trim()) },
                )
            }
            item {
                MenuChip(
                    label = "Search",
                    secondary = "Station name, e.g. jazz fm",
                    icon = Icons.Filled.Search,
                    enabled = query.isNotBlank(),
                ) { onSearch(query.trim()) }
            }
            item {
                Text(
                    text = "Or tap a suggestion",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            items(SUGGESTIONS.size, key = { SUGGESTIONS[it] }) { index ->
                val suggestion = SUGGESTIONS[index]
                MenuChip(
                    label = suggestion,
                    secondary = "Search this name",
                    icon = Icons.Filled.Search,
                ) { onSearch(suggestion) }
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

private val SUGGESTIONS = listOf("BBC Radio", "NPR", "Radio Paradise", "Jazz24", "KEXP")

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Type a station name",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2.copy(
                        color = MaterialTheme.colors.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}

// ---- browse ----------------------------------------------------------------

@Composable
private fun CategoriesScreen(
    kind: Route.Categories.Kind,
    onBack: () -> Unit,
    onPick: (String, StationSource) -> Unit,
) {
    BackHandler { onBack() }
    val listState = rememberScalingLazyListState()
    val entries: List<Pair<String, String>> = when (kind) {
        Route.Categories.Kind.GENRE -> Directory.genres.map { it.replaceFirstChar(Char::uppercase) to "Genre" }
        Route.Categories.Kind.COUNTRY -> Directory.countries
    }

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                BackChip(
                    label = if (kind == Route.Categories.Kind.GENRE) "Genres" else "Countries",
                    onBack = onBack,
                )
            }
            items(entries.size, key = { entries[it].first }) { index ->
                val (label, secondary) = entries[index]
                MenuChip(
                    label = label,
                    secondary = secondary,
                    icon = if (kind == Route.Categories.Kind.GENRE) Icons.Filled.Category else Icons.Filled.Public,
                ) {
                    val source = if (kind == Route.Categories.Kind.GENRE) {
                        StationSource.Tag(Directory.genres[index])
                    } else {
                        StationSource.Country(Directory.countries[index].first)
                    }
                    onPick(label, source)
                }
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ---- station results -------------------------------------------------------

@Composable
private fun StationsScreen(
    route: Route.Stations,
    state: PlaybackState,
    onBack: () -> Unit,
    onPlay: (Station) -> Unit,
) {
    BackHandler { onBack() }
    var loading by remember(route.source) { mutableStateOf(true) }
    var failure by remember(route.source) { mutableStateOf<String?>(null) }
    var stations by remember(route.source) { mutableStateOf<List<Station>>(emptyList()) }
    var attempt by remember(route.source) { mutableStateOf(0) }

    LaunchedEffect(route.source, attempt) {
        loading = true
        failure = null
        runCatching { load(route.source) }
            .onSuccess {
                stations = it
                loading = false
            }
            .onFailure {
                failure = it.message ?: it::class.java.simpleName
                loading = false
            }
    }

    StationListScreen(
        title = route.title,
        stations = stations,
        state = state,
        loading = loading,
        failure = failure,
        onRetry = { attempt += 1 },
        onBack = onBack,
        onPlay = onPlay,
    )
}

/** Fetches a list from the directory. Runs on the caller's IO dispatcher. */
private suspend fun load(source: StationSource): List<Station> = when (source) {
    is StationSource.Query -> RadioBrowser.search(source.query)
    is StationSource.Tag -> RadioBrowser.byTag(source.tag)
    is StationSource.Country -> RadioBrowser.byCountry(source.code)
    StationSource.Popular -> RadioBrowser.popular()
}

/**
 * The one list screen used for search results, tags, countries, popular and
 * favourites. [stations] of null means "read the favourites instead".
 */
@Composable
private fun StationListScreen(
    title: String,
    stations: List<Station>?,
    state: PlaybackState,
    onBack: () -> Unit,
    onPlay: (Station) -> Unit,
    loading: Boolean = false,
    failure: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    BackHandler { onBack() }
    val favourites by Favourites.items.collectAsState()
    val items = stations ?: favourites
    val listState = rememberScalingLazyListState()

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(0) { BackChip(label = title, onBack = onBack) }

            if (loading) {
                item(1) { CenteredMessage(spinner = true, text = "Loading stations…") }
            } else if (failure != null) {
                item(1) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colors.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Couldn't reach the station directory",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = failure,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                onRetry?.let { retry ->
                    item(2) {
                        MenuChip("Retry", "Ask the directory again", Icons.Filled.Refresh) { retry() }
                    }
                }
            } else if (items.isEmpty()) {
                item(1) {
                    CenteredMessage(
                        text = if (stations == null) {
                            "No favourites yet.\nPlay a station and tap the star."
                        } else {
                            "No working stations for this one.\nTry another."
                        },
                    )
                }
            } else {
                items(items.size, key = { items[it].uuid }) { index ->
                    val station = items[index]
                    StationChip(
                        station = station,
                        favourite = favourites.any { it.uuid == station.uuid },
                        playing = state.playing && state.station?.uuid == station.uuid,
                        onClick = { onPlay(station) },
                    )
                }
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun StationChip(
    station: Station,
    favourite: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = {
            Text(station.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(
                text = station.summary.ifEmpty { station.country.ifEmpty { "stream" } },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Icon(
                imageVector = when {
                    playing -> Icons.Filled.GraphicEq
                    favourite -> Icons.Filled.Star
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (playing) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
            )
        },
        colors = if (playing) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- shared pieces ---------------------------------------------------------

@Composable
private fun MenuChip(
    label: String,
    secondary: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One chip, doubling as the screen heading and the way back. */
@Composable
private fun BackChip(label: String, onBack: () -> Unit) {
    Chip(
        onClick = onBack,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredMessage(text: String, spinner: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary)
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
