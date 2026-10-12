package com.grocerylist.app.ui.list

import androidx.compose.foundation.background
import com.grocerylist.app.ui.glass
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.ItemStat
import com.grocerylist.app.data.ListItem
import com.grocerylist.app.ui.AddItemBar
import com.grocerylist.app.ui.MemberBadge
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.StoreLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(session: Session.Ready, listId: String, onBack: () -> Unit) {
    val viewModel: ListViewModel = viewModel { ListViewModel(session.householdId, listId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active = state.list?.isActive == true
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingFinish by remember { mutableStateOf(false) }
    val uncheckedCount = state.items.count { !it.checked }
    val finish = { carryOver: Boolean ->
        confirmingFinish = false
        viewModel.finishTrip(carryOver)
        onBack()
    }

    // The list scrolls under a frosted top bar and a floating glass add bar.
    val haze = rememberHazeState()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.hazeEffect(haze, style = glass()),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val storeName = state.list?.storeName.orEmpty()
                        if (storeName.isNotBlank()) {
                            StoreLogo(
                                storeName,
                                Modifier.padding(end = 12.dp).size(44.dp, 32.dp),
                                padding = 3.dp,
                                cornerRadius = 8.dp,
                                initialsSize = 13.sp,
                            )
                        }
                        Text(state.list?.displayName.orEmpty())
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.list != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (active) {
                                DropdownMenuItem(
                                    text = { Text("Finish & close out") },
                                    onClick = {
                                        menuOpen = false
                                        if (uncheckedCount > 0) confirmingFinish = true else finish(false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear checked items") },
                                    enabled = state.items.any { it.checked },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.clearChecked()
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Reopen list") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.reopen()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { if (active) AddItemBar(onAdd = viewModel::add, hazeState = haze) },
    ) { padding ->
        if (confirmingFinish) {
            AlertDialog(
                onDismissRequest = { confirmingFinish = false },
                title = { Text("Close out this list?") },
                text = {
                    Text(
                        if (uncheckedCount == 1) "1 item wasn't checked off." else "$uncheckedCount items weren't checked off.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { finish(true) }) { Text("Move to a new list") }
                },
                dismissButton = {
                    TextButton(onClick = { finish(false) }) { Text("Drop them") }
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize().hazeSource(haze), contentPadding = padding) {
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestions") { SuggestionRow(state.suggestions, onAdd = viewModel::add) }
            }
            if (state.items.isEmpty() && state.list != null) {
                item(key = "empty") {
                    Text(
                        if (active) "Nothing on this list yet" else "No items on this list",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            if (state.list?.isActive == false) {
                item(key = "closed-banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "This list is closed out. Reopen it from the ⋮ menu to make changes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            items(state.items, key = { it.id }) { item ->
                GroceryRow(
                    addedBy = state.otherMembers[item.addedBy],
                    item = item,
                    editable = active,
                    onToggle = { viewModel.toggle(item) },
                    onDelete = { viewModel.delete(item) },
                    modifier = Modifier.animateItem(),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestions: List<ItemStat>, onAdd: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Suggested",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(suggestions, key = { it.key }) { stat ->
                AssistChip(
                    onClick = { onAdd(stat.displayName) },
                    label = { Text(stat.displayName) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroceryRow(
    item: ListItem,
    editable: Boolean,
    addedBy: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val toggleWithFeel = {
        haptics.performHapticFeedback(if (item.checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        onToggle()
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            it == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = editable,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        val colors = MaterialTheme.colorScheme
        ListItem(
            modifier = Modifier.clickable(enabled = editable, onClick = toggleWithFeel),
            leadingContent = {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { toggleWithFeel() },
                    enabled = editable,
                    // Closed-out lists: a pale outline instead of the usual disabled tint, so it reads as read-only.
                    colors = CheckboxDefaults.colors(
                        disabledUncheckedColor = colors.outlineVariant,
                        disabledCheckedColor = colors.outlineVariant,
                    ),
                )
            },
            headlineContent = {
                // The strike-through draws across the word when an item is checked off.
                val strike by animateFloatAsState(
                    targetValue = if (item.checked) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "strike",
                )
                val textColor by animateColorAsState(
                    when {
                        !editable -> colors.onSurface.copy(alpha = 0.45f)
                        item.checked -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                    label = "text color",
                )
                Text(
                    item.name,
                    color = textColor,
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        if (strike > 0f) {
                            val y = size.height / 2
                            drawLine(textColor, Offset(0f, y), Offset(size.width * strike, y), strokeWidth = 1.5.dp.toPx())
                        }
                    },
                )
            },
            // Only items someone else added get a badge, so your own list stays clean.
            trailingContent = addedBy?.let { name -> { MemberBadge(name) } },
        )
    }
}
