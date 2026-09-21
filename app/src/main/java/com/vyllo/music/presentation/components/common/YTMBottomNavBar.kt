package com.vyllo.music.presentation.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.presentation.theme.VylloMotion
import com.vyllo.music.presentation.theme.VylloSize

@Composable
fun YTMBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Pair("Home", Icons.Rounded.Home),
        Pair("Search", Icons.Rounded.Search),
        Pair("Explore", Icons.Rounded.Explore),
        Pair("Library", Icons.Rounded.LibraryMusic)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = VylloSize.hairline
        )
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            tonalElevation = 0.dp
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedTab == index

                // Selection eases in rather than snapping, which makes tab switching
                // feel like one continuous gesture.
                val selectionProgress by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(VylloMotion.medium, easing = VylloMotion.standard),
                    label = "nav_selection"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    },
                    animationSpec = tween(VylloMotion.fast),
                    label = "nav_icon_tint"
                )
                val labelAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.6f,
                    animationSpec = tween(VylloMotion.fast),
                    label = "nav_label_alpha"
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier
                                .size(VylloSize.iconLarge)
                                .graphicsLayer {
                                    val s = 0.9f + (0.1f * selectionProgress)
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = labelAlpha)
                        )
                    },
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        indicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    )
                )
            }
        }
    }
}
