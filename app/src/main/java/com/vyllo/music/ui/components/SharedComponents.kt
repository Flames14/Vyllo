package com.vyllo.music.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vyllo.music.domain.model.MusicItem
import java.util.Calendar

@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= 33) {
        AuroraShader(modifier)
    } else {
        FallbackMeshGradient(modifier)
    }
}

@RequiresApi(33)
@Composable
fun AuroraShader(modifier: Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val isDark = isSystemInDarkTheme()
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { 
                time.floatValue = (it / 1_000_000_000f) 
            }
        }
    }
    
    val shader = remember(isDark) {
        if (Build.VERSION.SDK_INT >= 33) {
            val darkColor1 = if (isDark) "vec3(0.05, 0.05, 0.05)" else "vec3(0.95, 0.95, 0.95)"
            val darkColor2 = if (isDark) "vec3(0.15, 0.15, 0.15)" else "vec3(0.85, 0.85, 0.85)"
            val highlight  = if (isDark) "vec3(0.3, 0.3, 0.3)" else "vec3(0.7, 0.7, 0.7)"
            
            android.graphics.RuntimeShader("""
                uniform float2 resolution;
                uniform float time;
                
                vec4 main(vec2 fragCoord) {
                    vec2 uv = fragCoord / resolution.xy;
                    float t = time * 0.5;
                    
                    float r = sin(uv.x * 3.0 + t) * 0.5 + 0.5;
                    float g = sin(uv.y * 3.0 + t * 1.5) * 0.5 + 0.5;
                    float b = sin((uv.x + uv.y) * 3.0 + t * 0.5) * 0.5 + 0.5;
                    
                    vec3 color = mix($darkColor1, $darkColor2, r * g);
                    color = mix(color, $highlight, b * 0.5);
                    
                    return vec4(color, 1.0);
                }
            """.trimIndent())
        } else null
    }

    if (shader != null) {
        Canvas(modifier = modifier) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time.floatValue)
            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        FallbackMeshGradient(modifier)
    }
}

@Composable
fun FallbackMeshGradient(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )
    
    val isDark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    val blob1 = if(isDark) Color(0xFF202020) else Color(0xFFE0E0E0)
    val blob2 = if(isDark) Color(0xFF303030) else Color(0xFFD0D0D0)
    val blob3 = if(isDark) Color(0xFF151515) else Color(0xFFF0F0F0)

    Box(modifier = modifier.background(bg)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-100).dp + (100.dp * offset1), y = (-100).dp + (50.dp * offset2))
                .size(400.dp)
                .alpha(0.4f)
                .background(Brush.radialGradient(listOf(blob1, Color.Transparent)))
                .blur(40.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (100).dp - (100.dp * offset2), y = (100).dp - (50.dp * offset1))
                .size(500.dp)
                .alpha(0.3f)
                .background(Brush.radialGradient(listOf(blob2, Color.Transparent)))
                .blur(50.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (200.dp * (offset1 - 0.5f)), y = (200.dp * (offset2 - 0.5f)))
                .size(300.dp)
                .alpha(0.2f)
                .background(Brush.radialGradient(listOf(blob3, Color.Transparent)))
                .blur(45.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground.copy(0.03f)))
    }
}

@Composable
fun MagazineHeader() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good\nMorning."
        in 12..17 -> "Good\nAfternoon."
        else -> "Good\nEvening."
    }
    
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                lineHeight = 44.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Ready for some tunes?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun HeroRecommendationCard(item: MusicItem, isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(0.9f))
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "BEST MATCH",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.8f)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .shadow(16.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if(isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun PremiumSuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground.copy(0.9f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun HistorySuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground.copy(0.9f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun rememberLiquidFlingBehavior(enabled: Boolean): FlingBehavior {
    if (!enabled) return ScrollableDefaults.flingBehavior()

    val flingSpec = remember {
        exponentialDecay<Float>(
            frictionMultiplier = 0.35f,   
            absVelocityThreshold = 0.1f    
        )
    }
    return remember(flingSpec) {
        object : FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(
                initialVelocity: Float
            ): Float {
                if (kotlin.math.abs(initialVelocity) < 50f) return initialVelocity
                var lastValue = 0f
                val state = AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity
                )
                state.animateDecay(flingSpec) {
                    val delta = value - lastValue
                    lastValue = value
                    val consumed = scrollBy(delta)
                    if (delta != 0f && kotlin.math.abs(consumed / delta) < 0.5f) {
                        this.cancelAnimation()
                    }
                }
                return state.velocity
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isFloatingEnabled: Boolean,
    onFloatingEnabledChange: (Boolean) -> Unit,
    isBackgroundEnabled: Boolean,
    onBackgroundEnabledChange: (Boolean) -> Unit,
    isKeepAudioPlayingEnabled: Boolean = false,
    onKeepAudioPlayingChange: (Boolean) -> Unit = {},
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    isLiquidScrollEnabled: Boolean = false,
    onLiquidScrollChange: (Boolean) -> Unit = {},
    onCheckUpdateClick: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Settings", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "App Theme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf("System", "Light", "Dark", "Ocean", "Forest", "Sunset", "AMOLED", "Midnight Gold", "Cyberpunk", "Lavender", "Crimson")
                        themes.forEach { theme ->
                            val isSelected = theme == themeMode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onThemeModeChange(theme) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = theme,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Background Playback",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Continue playing when app is closed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isBackgroundEnabled,
                        onCheckedChange = onBackgroundEnabledChange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Keep Audio Playing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Don't pause when using mic or camera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isKeepAudioPlayingEnabled,
                        onCheckedChange = onKeepAudioPlayingChange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Floating Player",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Show bubble when leaving app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isFloatingEnabled,
                        onCheckedChange = onFloatingEnabledChange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Liquid Smooth Scroll",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "iPhone-like buttery smooth scrolling",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isLiquidScrollEnabled,
                        onCheckedChange = onLiquidScrollChange
                    )
                }

                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { uriHandler.openUri("https://ko-fi.com/betadeveloper") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Support Development",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Buy me a coffee ☕",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Donate",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCheckUpdateClick() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Check for Updates",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Download latest features and fixes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Icon(
                        Icons.Rounded.Update,
                        contentDescription = "Update",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}
