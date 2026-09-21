package com.vyllo.music.ui.components.background

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush

@RequiresApi(33)
@Composable
fun AuroraShader(modifier: Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(MaterialTheme.colorScheme.background)

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
            val highlight = if (isDark) "vec3(0.3, 0.3, 0.3)" else "vec3(0.7, 0.7, 0.7)"

            android.graphics.RuntimeShader(
                """
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
                """.trimIndent()
            )
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
