package com.grocerylist.app.ui.recipe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.ChatMessage
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.ReadableWidth
import androidx.compose.foundation.layout.wrapContentWidth
import com.grocerylist.app.ui.Session

/** Starting points for an empty chat. */
private val EXAMPLES = listOf(
    "Boneless chicken thighs in the dutch oven",
    "A quick vegetarian weeknight dinner",
    "Something quick with ground beef",
)

/** Chat with the AI for recipe ideas. iOS: RecipeChatView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeChatScreen(session: Session.Ready, onBack: () -> Unit, onOpenList: (String) -> Unit) {
    val viewModel: RecipeChatViewModel = viewModel { RecipeChatViewModel(session.householdId, session.user.uid) }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val viewing by viewModel.viewing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.userMessages.collect { snackbar.showSnackbar(it) } }

    viewing?.let { recipe ->
        val detail: RecipeViewModel = viewModel(key = "chat-detail") { RecipeViewModel(session.householdId, null, session.user.uid) }
        BackHandler { viewModel.viewing.value = null }
        RecipeDetail(
            recipe = recipe,
            viewModel = detail,
            onBack = { viewModel.viewing.value = null },
            onOpenList = onOpenList,
            actions = {
                ShareRecipeButton(recipe)
                SaveButton(saved = recipe in saved, onSave = { viewModel.save(recipe) })
            },
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, sending) {
        val last = messages.size + (if (sending) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask for a recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // On a wide screen the conversation stays a readable column down the middle.
        Column(Modifier.fillMaxSize().padding(padding).imePadding().wrapContentWidth().widthIn(max = ReadableWidth)) {
            if (messages.isEmpty() && !sending) {
                EmptyChat(onExample = viewModel::send, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(messages) { _, message ->
                        if (message.fromUser) {
                            UserBubble(message.text)
                        } else {
                            ReplyMessage(
                                message = message,
                                saved = saved,
                                onOpen = { viewModel.viewing.value = it },
                                onSave = viewModel::save,
                            )
                        }
                    }
                    if (sending) item { Thinking() }
                }
            }
            InputBar(
                text = draft,
                onChange = { viewModel.draft.value = it },
                sending = sending,
                onSend = { viewModel.send() },
            )
        }
    }
}

@Composable
private fun SaveButton(saved: Boolean, onSave: () -> Unit) {
    if (saved) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
            Text("Saved", Modifier.padding(start = 6.dp))
        }
    } else {
        FilledTonalButton(onClick = onSave, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onExample: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(AppIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Text("What do you feel like cooking?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Describe a dish, an ingredient, or the gear you want to use. You can save any recipe you like, " +
                "then add what you're missing to a list.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEach { example -> AssistChip(onClick = { onExample(example) }, label = { Text(example) }) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun ReplyMessage(message: ChatMessage, saved: Map<Recipe, String>, onOpen: (Recipe) -> Unit, onSave: (Recipe) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.text.isNotBlank()) Text(message.text, style = MaterialTheme.typography.bodyLarge)
        message.recipes.forEach { recipe ->
            SuggestionCard(recipe, saved = recipe in saved, onOpen = { onOpen(recipe) }, onSave = { onSave(recipe) })
        }
    }
}

@Composable
private fun SuggestionCard(recipe: Recipe, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium)
            val summary = recipeSummary(recipe)
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recipe.description.isNotBlank()) {
                Text(recipe.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen) { Text("View recipe") }
                SaveButton(saved = saved, onSave = onSave)
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(
        Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InputBar(text: String, onChange: (String) -> Unit, sending: Boolean, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            placeholder = { Text("Ask for a recipe…") },
            maxLines = 5,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = text.isNotBlank() && !sending) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
