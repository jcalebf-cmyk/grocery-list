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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.OnHandItem
import com.grocerylist.app.ui.AddItemBar
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

    fun add(name: String) = repo.addOnHand(name, uid)

    fun remove(item: OnHandItem) = repo.removeOnHand(item)
}

/** "My kitchen": ingredients the household already has. Recipe ideas use them; shopping suggestions don't. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnHandScreen(session: Session.Ready, onBack: () -> Unit) {
    val viewModel: OnHandViewModel = viewModel { OnHandViewModel(session.householdId, session.user.uid) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val quickAdds by viewModel.quickAdds.collectAsStateWithLifecycle()

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
        bottomBar = { AddItemBar(onAdd = viewModel::add, placeholder = "Add an ingredient") },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    "Things you already have, like a stocked freezer or a ButcherBox delivery. " +
                        "Recipes for you will put these to use first.",
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
                                    onClick = { viewModel.add(name) },
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
                        "In your kitchen",
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
