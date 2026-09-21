package com.vyllo.music.presentation.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vyllo.music.presentation.components.ytmClickable
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing

@Composable
fun YTMSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = VylloSpacing.screenHorizontal,
                end = if (onSeeAll != null) VylloSpacing.sm else VylloSpacing.screenHorizontal,
                top = VylloSpacing.sectionHeaderVertical,
                bottom = VylloSpacing.sectionHeaderVertical
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // The chevron is only rendered when there is somewhere to go.
        // Previously it was always drawn but never wired up, which advertised a
        // tap target that did nothing.
        if (onSeeAll != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = VylloSize.minTouchTarget)
                    .widthIn(min = VylloSize.minTouchTarget)
                    .clip(CircleShape)
                    .ytmClickable(onClick = onSeeAll)
                    .semantics { contentDescription = "See all $title" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                    modifier = Modifier.size(VylloSize.iconLarge)
                )
            }
        }
    }
}
