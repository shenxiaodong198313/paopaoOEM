package com.paopaoime.aikeyboard.ime.view

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.RawRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class CenterCropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val playerView = PlayerView(context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private var exoPlayer: ExoPlayer? = null
    private var videoResId: Int? = null
    private var prepared = false
    private var firstFrameRendered = false
    private var playWhenReady = false
    private var prewarmRequested = false
    private var hostResumed = false
    private var firstFrameRenderedListener: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    prepared = true
                    updatePlaybackState()
                }

                Player.STATE_ENDED -> {
                    playWhenReady = false
                    exoPlayer?.seekTo(0)
                    updatePlaybackState()
                }
            }
        }

        override fun onRenderedFirstFrame() {
            if (firstFrameRendered) return
            firstFrameRendered = true
            firstFrameRenderedListener?.invoke()
            if (prewarmRequested) {
                prewarmRequested = false
                exoPlayer?.pause()
                exoPlayer?.playWhenReady = false
                exoPlayer?.seekTo(0)
            }
        }
    }

    init {
        addView(playerView)
    }

    fun setVideoResource(@RawRes resId: Int) {
        videoResId = resId
        recreatePlayer()
    }

    fun onHostResume() {
        hostResumed = true
        updatePlaybackState()
    }

    fun onHostPause() {
        hostResumed = false
        exoPlayer?.pause()
    }

    fun hasRenderedFirstFrame(): Boolean = firstFrameRendered

    fun setOnFirstFrameRenderedListener(listener: (() -> Unit)?) {
        firstFrameRenderedListener = listener
        if (firstFrameRendered) {
            listener?.invoke()
        }
    }

    fun playFromStart() {
        prewarmRequested = false
        playWhenReady = true
        exoPlayer?.seekTo(0)
        updatePlaybackState()
    }

    fun preloadFirstFrameSilently() {
        if (firstFrameRendered) {
            stopSilentlyAtStart()
            return
        }
        prewarmRequested = true
        playWhenReady = false
        updatePlaybackState()
    }

    fun stopSilentlyAtStart() {
        prewarmRequested = false
        playWhenReady = false
        exoPlayer?.volume = 0f
        exoPlayer?.playWhenReady = false
        exoPlayer?.pause()
        if (prepared) {
            exoPlayer?.seekTo(0)
        }
    }

    fun pausePlayback(resetToStart: Boolean) {
        prewarmRequested = false
        playWhenReady = false
        exoPlayer?.volume = 0f
        exoPlayer?.pause()
        if (resetToStart && prepared) {
            exoPlayer?.seekTo(0)
        }
    }

    fun releasePlayer() {
        playWhenReady = false
        prepared = false
        firstFrameRendered = false
        prewarmRequested = false
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun recreatePlayer() {
        val resId = videoResId ?: return
        releasePlayer()
        firstFrameRendered = false
        prewarmRequested = false
        val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$resId"))
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            addListener(playerListener)
            setMediaItem(mediaItem)
            prepare()
        }
        playerView.player = exoPlayer
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val player = exoPlayer ?: return
        val shouldPlayAudibly = prepared && hostResumed && playWhenReady
        val shouldPrewarmSilently = prepared && hostResumed && prewarmRequested && !firstFrameRendered
        player.volume = if (shouldPlayAudibly) 1f else 0f
        player.playWhenReady = shouldPlayAudibly || shouldPrewarmSilently
        if (!shouldPlayAudibly && !shouldPrewarmSilently) {
            player.pause()
        }
    }
}
