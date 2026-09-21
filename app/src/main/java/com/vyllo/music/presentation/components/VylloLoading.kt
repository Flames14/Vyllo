package com.vyllo.music.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vyllo.music.presentation.theme.VylloMotion
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Shared loading placeholders (skeletons).
 *
 * Before this file each screen rendered its own ad-hoc `CircularProgressIndicator`,
 * which made loading feel abrupt and inconsistent. These components let screens show
 * the shape of the content that is about to arrive.
 */

/**
 * A brushed shimmer brush used for skeleton placeholders.
 * All skeletons on a screen share the same infinite transition when the brush is
 * hoisted by the caller, keeping the animation cost constant.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "vyllo_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(VylloMotion.shimmer),
            repeatMode = RepeatMode.Restart
        ),
        label = "vyllo_shimmer_progress"
    )

    val base = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)

    // Sweep the highlight band from left to right.
    val startX = -300f + (progress * 900f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(startX, 0f),
        end = Offset(startX + 300f, 300f)
    )
}

/** A single shimmering rounded block. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = VylloRadius.sm,
    brush: Brush? = null
) {
    val shimmer = brush ?: rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmer)
    )
}

/** Stand-in for a [YTMSongRow] while content is loading. */
@Composable
fun SkeletonSongRow(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    showArtwork: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VylloSpacing.screenHorizontal, vertical = VylloSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArtwork) {
            SkeletonBlock(
                modifier = Modifier.size(VylloSize.artworkRow),
                cornerRadius = VylloRadius.sm,
                brush = brush
            )
            Spacer(modifier = Modifier.width(VylloSpacing.lg))
        }
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.62f).height(12.dp),
                cornerRadius = VylloRadius.xs,
                brush = brush
            )
            Spacer(modifier = Modifier.height(VylloSpacing.sm))
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.38f).height(10.dp),
                cornerRadius = VylloRadius.xs,
                brush = brush
            )
        }
    }
}

/** A small horizontal card placeholder used inside carousel sections. */
@Composable
fun SkeletonSquareCard(
    modifier: Modifier = Modifier,
    brush: Brush? = null
) {
    Column(modifier = modifier.width(VylloSize.artworkCard)) {
        SkeletonBlock(
            modifier = Modifier.size(VylloSize.artworkCard),
            cornerRadius = VylloRadius.sm,
            brush = brush
        )
        Spacer(modifier = Modifier.height(VylloSpacing.sm))
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth(0.8f).height(10.dp),
            cornerRadius = VylloRadius.xs,
            brush = brush
        )
        Spacer(modifier = Modifier.height(VylloSpacing.xs))
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth(0.5f).height(9.dp),
            cornerRadius = VylloRadius.xs,
            brush = brush
        )
    }
}

/**
 * Home/Explore style skeleton: a header block plus a couple of carousel placeholders.
 * Used while the first page of content is still loading so users never stare at a
 * blank screen.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
    ) {
        // Header placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VylloSpacing.screenHorizontal, vertical = VylloSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBlock(
                modifier = Modifier.size(32.dp),
                cornerRadius = VylloRadius.pill,
                brush = brush
            )
            Spacer(modifier = Modifier.width(VylloSpacing.sm))
            SkeletonBlock(
                modifier = Modifier.width(96.dp).height(20.dp),
                cornerRadius = VylloRadius.xs,
                brush = brush
            )
        }

        // Chip row placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VylloSpacing.screenHorizontal, vertical = VylloSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(VylloSpacing.sm)
        ) {
            listOf(64.dp, 84.dp, 72.dp).forEach { width ->
                SkeletonBlock(
                    modifier = Modifier.width(width).height(32.dp),
                    cornerRadius = VylloRadius.sm,
                    brush = brush
                )
            }
        }

        // Two carousel placeholders
        repeat(2) { sectionIndex ->
            Spacer(modifier = Modifier.height(VylloSpacing.md))
            Column {
                SkeletonBlock(
                    modifier = Modifier
                        .padding(horizontal = VylloSpacing.screenHorizontal)
                        .width(if (sectionIndex == 0) 132.dp else 108.dp)
                        .height(18.dp),
                    cornerRadius = VylloRadius.xs,
                    brush = brush
                )
                Spacer(modifier = Modifier.height(VylloSpacing.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VylloSpacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(VylloSpacing.lg)
                ) {
                    repeat(3) {
                        SkeletonSquareCard(brush = brush)
                    }
                }
            }
        }
    }
}

/** List skeleton used by search results and library lists. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
    ) {
        repeat(rows) {
            SkeletonSongRow(brush = brush)
        }
    }
}