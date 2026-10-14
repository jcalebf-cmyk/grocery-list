package com.grocerylist.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import com.grocerylist.app.data.NearbyStore
import com.grocerylist.app.data.NearbyStores
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.StoreBrands
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Full-screen grid for choosing a store: the 8 closest grocery stores, then the household's other
 * saved stores. Picking a nearby store that isn't saved yet, or typing one in, calls [onPickNew]
 * with its name; the caller saves it. When [onSkip] is set, the user can continue without a store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePicker(
    stores: List<Store>,
    onPick: (Store) -> Unit,
    onPickNew: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Where are you shopping?",
    openStoreIds: Set<String> = emptySet(),
    onSkip: (() -> Unit)? = null,
    onRemove: ((Store) -> Unit)? = null,
) {
    var addingStore by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Store?>(null) }
    val location = rememberLocationPermissionState()
    // Ask the first time the picker opens, when the reason is obvious. If declined, the
    // Near you section explains what location is for and how to turn it on. People who allowed
    // only approximate location before precise was offered are asked once more.
    LaunchedEffect(Unit) {
        val firstTime = !location.granted && !location.askedBefore
        val offerPrecise = location.granted && !location.precise && !location.askedPrecise
        if (firstTime || offerPrecise) location.request()
    }
    val nearby = rememberNearbyStores(location.granted)
    val savedByName = stores.associateBy { it.name.lowercase() }
    val nearbyStores = (nearby as? NearbyState.Loaded)?.stores.orEmpty().take(NEARBY_SHOWN)
    // Saved stores already shown under Near you aren't repeated below.
    val otherSaved = stores.filterNot { s -> nearbyStores.any { it.name.equals(s.name, ignoreCase = true) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    },
                )
            },
            bottomBar = {
                if (onSkip != null) {
                    Surface(tonalElevation = 3.dp) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        ) { Text("Skip, start a list without a store") }
                    }
                }
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                header("Near you")
                when {
                    !location.granted -> fullWidth("location") {
                        LocationPrompt(
                            needsSettings = location.needsSettings,
                            onAllow = location::request,
                            onOpenSettings = location::openSettings,
                        )
                    }
                    nearby is NearbyState.Loading -> items(NEARBY_SHOWN, key = { "placeholder-$it" }) { PlaceholderTile() }
                    nearby is NearbyState.Failed -> fullWidth("nearby-failed") {
                        StatusRow("Couldn't load nearby stores. Tap to try again.", onClick = nearby.retry)
                    }
                    nearbyStores.isEmpty() -> fullWidth("nearby-empty") {
                        Text(
                            "No grocery stores within 10 miles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    else -> items(nearbyStores, key = { "nearby-${it.name}" }) { store ->
                        val saved = savedByName[store.name.lowercase()]
                        StoreTile(
                            name = store.name,
                            subtitle = formatDistance(store.distanceMeters),
                            badge = if (saved != null && saved.id in openStoreIds) "List open" else null,
                            onClick = { if (saved != null) onPick(saved) else onPickNew(store.name) },
                        )
                    }
                }

                header(if (otherSaved.isNotEmpty()) "Your stores" else "Somewhere else?")
                items(otherSaved, key = { it.id }) { store ->
                    StoreTile(
                        name = store.name,
                        badge = if (store.id in openStoreIds) "List open" else null,
                        onClick = { onPick(store) },
                        onLongClick = onRemove?.let { { removing = store } },
                    )
                }
                item(key = "add") { AddStoreTile(onClick = { addingStore = true }) }
            }
        }
    }

    removing?.let { store ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${store.name}?") },
            text = { Text("It won't show under Your stores. Its lists stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove?.invoke(store)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    if (addingStore) {
        AddStoreDialog(
            onAdd = { name ->
                addingStore = false
                onPickNew(name)
            },
            onDismiss = { addingStore = false },
        )
    }
}

private fun LazyGridScope.header(text: String) {
    item(key = "header-$text", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private const val NEARBY_SHOWN = 8

private sealed interface NearbyState {
    data object Loading : NearbyState
    data class Failed(val retry: () -> Unit) : NearbyState
    data class Loaded(val stores: List<NearbyStore>) : NearbyState
}

/**
 * Nearby stores once location is allowed. Saved results show immediately; meanwhile the phone's
 * location is checked and a new lookup runs only if the saved one is stale or from elsewhere.
 */
@Composable
private fun rememberNearbyStores(locationGranted: Boolean): NearbyState {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember {
        mutableStateOf<NearbyState>(NearbyStores.peek(context)?.let { NearbyState.Loaded(it) } ?: NearbyState.Loading)
    }
    LaunchedEffect(locationGranted, attempt) {
        if (!locationGranted) return@LaunchedEffect
        val showing = state as? NearbyState.Loaded
        if (showing == null) state = NearbyState.Loading
        state = try {
            NearbyState.Loaded(NearbyStores.findCached(context))
        } catch (_: Exception) {
            // Keep showing what we had rather than replacing it with an error.
            showing ?: NearbyState.Failed(retry = { attempt++ })
        }
    }
    return state
}

/** Explains why location helps and how to turn it on. */
@Composable
private fun LocationPrompt(needsSettings: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("See grocery stores near you", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (needsSettings) {
                    "Location is turned off for this app. Turn it on in Settings to see the 8 closest " +
                        "grocery stores here and how far away they are."
                } else {
                    "Allow location to see the 8 closest grocery stores here and how far away they are."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (needsSettings) {
                FilledTonalButton(onClick = onOpenSettings) { Text("Open settings") }
            } else {
                FilledTonalButton(onClick = onAllow) { Text("Allow location") }
            }
        }
    }
}

@Composable
private fun PlaceholderTile() {
    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.94f).aspectRatio(1.25f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
        Surface(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(12.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
    }
}

@Composable
private fun StatusRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StoreTile(
    name: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StoreLogo(
            name,
            Modifier.padding(top = 4.dp).fillMaxWidth(0.94f).aspectRatio(1.25f),
            padding = 10.dp,
            cornerRadius = 16.dp,
            elevation = 3.dp,
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        when {
            badge != null -> Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            subtitle != null -> Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AddStoreTile(onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.94f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
        Text(
            "Add store",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun AddStoreDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a store") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Store name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatDistance(meters: Int): String {
    val usesMiles = Locale.getDefault().country in setOf("US", "GB", "LR", "MM")
    return if (usesMiles) "%.1f mi".format(meters / 1609.34) else "%.1f km".format(meters / 1000.0)
}
