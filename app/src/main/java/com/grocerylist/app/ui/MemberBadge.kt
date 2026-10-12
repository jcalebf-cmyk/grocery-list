package com.grocerylist.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** A small initial in a member's color, e.g. on list items they added. Each name always gets the same color. */
@Composable
fun MemberBadge(name: String, modifier: Modifier = Modifier) {
    val color = MEMBER_COLORS[name.lowercase().hashCode().absoluteValue % MEMBER_COLORS.size]
    Surface(
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
        modifier = modifier.size(26.dp).semantics { contentDescription = "Added by $name" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val MEMBER_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFFEF6C00),
    Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF2E7D32), Color(0xFFC62828),
)
