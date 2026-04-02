package com.vyllo.music

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    private lateinit var videoView: VideoView
    private val handler = Handler(Looper.getMainLooper())
    private val splashTimeOut: Long = 3500 // 3.5 seconds for video to play

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.splashVideoView)
        setupVideo()
    }

    private fun setupVideo() {
        try {
            val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.splash_video}")
            videoView.setVideoURI(videoUri)
            
            videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = false
                mediaPlayer.start()
                
                // Navigate to MainActivity when video ends
                mediaPlayer.setOnCompletionListener {
                    navigateToMain()
                }
            }

            videoView.setOnErrorListener { _, _, _ ->
                // If video fails to load, navigate to main after a short delay
                handler.postDelayed({ navigateToMain() }, 1000)
                true
            }

            // Fallback timeout - navigate to main even if video doesn't complete
            handler.postDelayed({ navigateToMain() }, splashTimeOut)

        } catch (e: Exception) {
            e.printStackTrace()
            // If video setup fails, navigate to main after a short delay
            handler.postDelayed({ navigateToMain() }, 500)
        }
    }

    private fun navigateToMain() {
        handler.post {
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            videoView.stopPlayback()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
