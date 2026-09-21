package com.vyllo.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Shared empty, error and short-loading surfaces.
 *
 * Rule of thumb implemented here: every empty/error state answers
 * "what happened", "why it is empty" and "what can I do next".
 *
 * These replace the previous bare `Text("No songs in this playlist")` style
 * placeholders which gave the user nothing to act on.
 */

/**
 * Friendly empty state with an optional primary action.
 *
 * @param icon illustration shown inside a soft circular container
 * @param title short headline, e.g. "No downloads yet"
 * @param message one sentence explaining what will appear here
 * @param actionLabel optional button label, e.g. "Browse songs"
 * @param onAction invoked when the action button is tapped
 */
@Composable
fun VylloEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VylloSpacing.xxxl, vertical = VylloSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }

        Spacer(modifier = Modifier.height(VylloSpacing.xl))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(VylloSpacing.sm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(VylloSpacing.xxl))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(VylloRadius.pill),
                contentPadding = PaddingValues(
                    horizontal = VylloSpacing.xxl,
                    vertical = VylloSpacing.md
                )
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(VylloSpacing.xs))
            TextButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Error state that explains the problem in plain language and always offers recovery.
 * The underlying error logic is untouched — this is purely the presentation layer.
 */
@Composable
fun VylloErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VylloSpacing.xxxl, vertical = VylloSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(VylloSpacing.xl))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(VylloSpacing.sm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(VylloSpacing.xxl))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(VylloRadius.pill),
                contentPadding = PaddingValues(
                    horizontal = VylloSpacing.xxl,
                    vertical = VylloSpacing.md
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(VylloSpacing.sm))
                Text(retryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Centered indeterminate spinner with an optional caption.
 * Used for short, indeterminate waits where a skeleton would be misleading.
 */
@Composable
fun VylloLoadingIndicator(
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(VylloSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        if (caption != null) {
            Spacer(modifier = Modifier.height(VylloSpacing.md))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}