package com.vyllo.music.ui.components.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vyllo.music.presentation.components.ytmClickable
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing

@Composable
fun PremiumSuggestionRow(
    text: String,
    onClick: () -> Unit,
    onInsert: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VylloRadius.md))
            .ytmClickable(onClick = onClick)
            .padding(vertical = VylloSpacing.sm, horizontal = VylloSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                modifier = Modifier.size(VylloSize.iconSmall)
            )
        }
        Spacer(Modifier.width(VylloSpacing.lg))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onInsert != null) {
            IconButton(
                onClick = onInsert,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NorthWest,
                    contentDescription = "Fill search box with \u201C$text\u201D",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.size(VylloSize.iconMedium)
                )
            }
        }
    }
}
