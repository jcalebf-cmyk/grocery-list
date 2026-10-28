package com.grocerylist.app.ui.onhand

import androidx.compose.foundation.layout.fillMaxSize
import com.grocerylist.app.data.kitchenQuickAdds
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.grocerylist.app.data.pantryQuickAdds
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.OnHandItem
import com.grocerylist.app.ui.AddItemBar
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class OnHandViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)

    val items = repo.observeOnHand().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickAdds = combine(repo.observeOnHand(), repo.observeStats()) { kitchen, stats ->
        kitchenQuickAdds(stats, kitchen.mapTo(HashSet()) { it.key }, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pantryQuickAdds = combine(repo.observeOnHand(), AppGraph.recipes(householdId).observePreferences()) { kitchen, prefs ->
        pantryQuickAdds(prefs.cuisines, kitchen.mapTo(HashSet()) { it.key })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, pantry: Boolean) = repo.addOnHand(name, uid, pantry)

    fun remove(item: OnHandItem) = repo.removeOnHand(item)
}

/**
 * "My kitchen": ingredients the household already has, in two tabs. Ingredients are what recipes get
 * built around; pantry seasonings and sauces just flavor them. List suggestions skip both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnHandScreen(session: Session.Ready, onBack: () -> Unit) {
    val viewModel: OnHandViewModel = viewModel { OnHandViewModel(session.householdId, session.user.uid) }
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    val kitchenQuickAdds by viewModel.quickAdds.collectAsStateWithLifecycle()
    val pantryQuickAdds by viewModel.pantryQuickAdds.collectAsStateWithLifecycle()
    var pantry by rememberSaveable { mutableStateOf(false) }
    val items = allItems.filter { it.isPantry == pantry }
    val quickAdds = if (pantry) pantryQuickAdds else kitchenQuickAdds

    val haze = rememberHazeState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My kitchen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            AddItemBar(
                onAdd = { viewModel.add(it, pantry) },
                placeholder = if (pantry) "Add a seasoning or sauce" else "Add an ingredient",
                hazeState = haze,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().hazeSource(haze), contentPadding = padding) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    listOf("Ingredients", "Pantry & seasonings").forEachIndexed { i, label ->
                        // No checkmark, and the text shrinks to fit, so "Pantry & seasonings" isn't cut off on narrow phones.
                        SegmentedButton(
                            selected = pantry == (i == 1),
                            onClick = { pantry = i == 1 },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) {
                            Text(
                                label,
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    if (pantry) {
                        "Spices, sauces, and other flavor-makers, like soy sauce or mirin. " +
                            "Recipes use them freely without building a dish around them."
                    } else {
                        "Things you already have, like a stocked freezer or a ButcherBox delivery. " +
                            "Lists won't suggest them, and recipes for you put them to use first."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (quickAdds.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Quick add", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        ) {
                            quickAdds.forEach { name ->
                                AssistChip(
                                    onClick = { viewModel.add(name, pantry) },
                                    label = { Text(name) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                item {
                    Text(
                        if (pantry) "In your pantry" else "In your kitchen",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                ListItem(
                    modifier = Modifier.animateItem(),
                    headlineContent = { Text(item.name) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.remove(item) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${item.name}")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
