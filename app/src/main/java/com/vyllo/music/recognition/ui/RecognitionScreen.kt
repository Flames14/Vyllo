package com.vyllo.music.recognition.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vyllo.music.recognition.domain.model.RecognitionStatus
import com.vyllo.music.recognition.presentation.RecognitionViewModel
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.delay

@Composable
fun RecognitionScreen(
    viewModel: RecognitionViewModel,
    onBack: () -> Unit,
    onTrackFound: (com.vyllo.music.domain.model.MusicItem) -> Unit
) {
    val status by viewModel.status.collectAsState()
    val context = LocalContext.current
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.startRecognition()
        }
    }

    // Handle success transition - now we stay on screen to show results
    LaunchedEffect(status) {
        if (status is RecognitionStatus.Success) {
            // We just stay here and show the result card
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (status) {
                    is RecognitionStatus.Idle -> "Tap to Recognize"
                    is RecognitionStatus.Listening -> "Listening..."
                    is RecognitionStatus.Processing -> "Identifying..."
                    is RecognitionStatus.Success -> "Found Match!"
                    is RecognitionStatus.NoMatch -> "No Match Found"
                    is RecognitionStatus.Error -> "Error"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))

            RecognitionCircle(
                isListening = status is RecognitionStatus.Listening || status is RecognitionStatus.Processing,
                isSuccess = status is RecognitionStatus.Success,
                onClick = {
                    if (hasPermission) {
                        viewModel.startRecognition()
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (val currentStatus = status) {
                is RecognitionStatus.Success -> {
                    SongResultCard(
                        result = currentStatus.result,
                        onPlay = { 
                            viewModel.resolveAndPlay(currentStatus.result) { musicItem ->
                                onTrackFound(musicItem)
                            }
                        }
                    )
                }
                is RecognitionStatus.Error -> {
                    Text(
                        text = currentStatus.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                is RecognitionStatus.NoMatch -> {
                    Text(
                        text = currentStatus.message,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.startRecognition() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Try Again")
                    }
                }
                else -> {
                    Text(
                        text = "Identify the music playing around you",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        IconButton(
            onClick = {
                viewModel.stopRecognition()
                onBack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
fun RecognitionCircle(
    isListening: Boolean,
    isSuccess: Boolean,
    onClick: () -> Unit
) {
    if (isSuccess) return // Don't show mic if success

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale * 1.1f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
            )
        }

        Surface(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Recognize",
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize(),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun SongResultCard(
    result: com.vyllo.music.recognition.domain.model.RecognitionResult,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.shape.RoundedCornerShape(16.dp).let { shape ->
                coil.compose.AsyncImage(
                    model = result.coverArtHqUrl ?: result.coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(shape)
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = result.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = result.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            if (result.album != null) {
                Text(
                    text = result.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play Song", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
