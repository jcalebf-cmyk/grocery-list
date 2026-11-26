package com.grocerylist.app.ui.recipe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.grocerylist.app.data.RecipeChats
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

/**
 * Chat with the AI for recipe ideas. The conversation stays on the phone until Start over, so coming back picks it up.
 * iOS: RecipeChatView.swift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeChatScreen(session: Session.Ready, onBack: () -> Unit, onOpenList: (String) -> Unit) {
    val context = LocalContext.current
    val viewModel: RecipeChatViewModel = viewModel {
        RecipeChatViewModel(RecipeChats.session(context.applicationContext, session.householdId, session.user.uid))
    }
    val chat = viewModel.chat
    val messages by chat.messages.collectAsStateWithLifecycle()
    val sending by chat.sending.collectAsStateWithLifecycle()
    val draft by chat.draft.collectAsStateWithLifecycle()
    val saved by chat.saved.collectAsStateWithLifecycle()
    val viewing by viewModel.viewing.collectAsStateWithLifecycle()
    var confirmingStartOver by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { chat.userMessages.collect { snackbar.showSnackbar(it) } }

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
                SaveButton(saved = recipe in saved, onSave = { chat.save(recipe) })
            },
        )
        return
    }

    val listState = rememberLazyListState(
        // Coming back to a conversation opens on its latest message.
        initialFirstVisibleItemIndex = (messages.size - 1).coerceAtLeast(0),
    )
    LaunchedEffect(messages.size, sending) {
        val last = messages.size + (if (sending) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    // Messages already there when the screen opened just show; new ones animate in.
    var shown by remember { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size) { shown = messages.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask for a recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    AnimatedVisibility(messages.isNotEmpty() || sending, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        TextButton(onClick = { confirmingStartOver = true }) { Text("Start over") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // On a wide screen the conversation stays a readable column down the middle.
        Column(Modifier.fillMaxSize().padding(padding).imePadding().wrapContentWidth().widthIn(max = ReadableWidth)) {
            AnimatedContent(
                targetState = messages.isEmpty() && !sending,
                transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                modifier = Modifier.weight(1f),
                label = "chat",
            ) { empty ->
                if (empty) {
                    EmptyChat(onExample = chat::send, modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(messages, key = { i, _ -> i }) { i, message ->
                            val animate = i >= shown
                            if (message.fromUser) {
                                AppearIn(animate, fromEnd = true) { UserBubble(message.text) }
                            } else {
                                AppearIn(animate, fromEnd = false) {
                                    ReplyMessage(
                                        message = message,
                                        animate = animate,
                                        saved = saved,
                                        onOpen = { viewModel.viewing.value = it },
                                        onSave = chat::save,
                                    )
                                }
                            }
                        }
                        if (sending) item(key = "thinking") { AppearIn(true, fromEnd = false) { Thinking() } }
                    }
                }
            }
            InputBar(
                text = draft,
                onChange = { chat.draft.value = it },
                sending = sending,
                onSend = { chat.send() },
            )
        }
    }

    if (confirmingStartOver) {
        AlertDialog(
            onDismissRequest = { confirmingStartOver = false },
            title = { Text("Start a new chat?") },
            text = { Text("This conversation will be cleared. Recipes you saved stay in your recipe box.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStartOver = false
                    chat.startOver()
                }) { Text("Start over") }
            },
            dismissButton = { TextButton(onClick = { confirmingStartOver = false }) { Text("Cancel") } },
        )
    }
}

/** A new message rises and grows into place from its side of the screen; one that was already there just shows. */
@Composable
private fun AppearIn(animate: Boolean, fromEnd: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(!animate) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(220)) +
            slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
            scaleIn(
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(if (fromEnd) 1f else 0f, 1f),
            ),
    ) { content() }
}

@Composable
private fun SaveButton(saved: Boolean, onSave: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    AnimatedContent(
        targetState = saved,
        transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.8f)) togetherWith
                fadeOut(tween(100))
        },
        label = "save",
    ) { isSaved ->
        if (isSaved) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                Text("Saved", Modifier.padding(start = 6.dp))
            }
        } else {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSave()
                },
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onExample: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // The sparkle pops in, then twinkles now and then.
        val pop = remember { MutableTransitionState(false) }.apply { targetState = true }
        val twinkle = rememberInfiniteTransition(label = "twinkle")
        val glow by twinkle.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "glow",
        )
        AnimatedVisibility(pop, enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) + fadeIn()) {
            Icon(
                AppIcons.Sparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp).graphicsLayer {
                    scaleX = glow
                    scaleY = glow
                    rotationZ = (glow - 1f) * 60f
                },
            )
        }
        Text("What do you feel like cooking?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Describe a dish, an ingredient, or the gear you want to use. You can save any recipe you like, " +
                "then add what you're missing to a list.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEachIndexed { i, example ->
                // The examples arrive one after another.
                val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(
                    shown,
                    enter = fadeIn(tween(300, delayMillis = 150 + i * 80)) +
                        slideInVertically(tween(350, delayMillis = 150 + i * 80)) { it / 2 },
                ) {
                    AssistChip(onClick = { onExample(example) }, label = { Text(example) })
                }
            }
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
private fun ReplyMessage(
    message: ChatMessage,
    animate: Boolean,
    saved: Map<Recipe, String>,
    onOpen: (Recipe) -> Unit,
    onSave: (Recipe) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.text.isNotBlank()) Text(message.text, style = MaterialTheme.typography.bodyLarge)
        message.recipes.forEachIndexed { i, recipe ->
            // A new reply's recipe cards deal in one after another, under its message.
            val shown = remember { MutableTransitionState(!animate) }.apply { targetState = true }
            val delay = 160 + i * 110
            AnimatedVisibility(
                shown,
                enter = fadeIn(tween(260, delayMillis = delay)) +
                    slideInVertically(tween(380, delayMillis = delay)) { it / 4 },
            ) {
                SuggestionCard(recipe, saved = recipe in saved, onOpen = { onOpen(recipe) }, onSave = { onSave(recipe) })
            }
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

/** Three dots bobbing in a reply bubble while the AI writes. */
@Composable
private fun Thinking() {
    val bob = rememberInfiniteTransition(label = "thinking")
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "Thinking" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val lift by bob.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 130)),
                label = "dot $i",
            )
            Box(
                Modifier
                    .offset(y = (-5).dp * lift)
                    .size(8.dp)
                    .graphicsLayer { alpha = 0.35f + 0.65f * lift }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
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
