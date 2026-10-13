package com.paopaoime.aikeyboard.ime.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch

class PackedAlphaVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var renderThread: RenderThread? = null
    private var decoderSurface: Surface? = null
    private var videoResId: Int? = null
    private var prepared = false
    private var playWhenReady = false
    private var hostResumed = false

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun setVideoResource(@RawRes resId: Int) {
        videoResId = resId
        recreateMediaPlayerIfPossible()
    }

    fun onHostResume() {
        hostResumed = true
        if (prepared && playWhenReady) {
            mediaPlayer?.start()
        }
    }

    fun onHostPause() {
        hostResumed = false
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun playFromStart() {
        playWhenReady = true
        if (prepared && hostResumed) {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
        }
    }

    fun pausePlayback(resetToStart: Boolean) {
        playWhenReady = false
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            if (resetToStart && prepared) {
                player.seekTo(0)
            }
        }
    }

    fun releasePlayer() {
        playWhenReady = false
        prepared = false
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        decoderSurface?.release()
        decoderSurface = null
        renderThread?.release()
        renderThread = null
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startRenderThread(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.updateViewport(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startRenderThread(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        renderThread?.release()
        renderThread = RenderThread(
            outputSurfaceTexture = surfaceTexture,
            initialWidth = width,
            initialHeight = height,
            onDecoderSurfaceReady = { surface ->
                post {
                    decoderSurface?.release()
                    decoderSurface = surface
                    recreateMediaPlayerIfPossible()
                }
            }
        ).also { it.start() }
    }

    private fun recreateMediaPlayerIfPossible() {
        val surface = decoderSurface ?: return
        val resId = videoResId ?: return
        prepared = false
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setSurface(surface)
            setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
            isLooping = false
            setOnPreparedListener { player ->
                prepared = true
                renderThread?.updateVideoSize(
                    (player.videoWidth / 2).coerceAtLeast(1),
                    player.videoHeight.coerceAtLeast(1)
                )
                if (playWhenReady && hostResumed) {
                    player.seekTo(0)
                    player.start()
                }
            }
            setOnCompletionListener {
                playWhenReady = false
            }
            setOnErrorListener { _, _, _ ->
                prepared = false
                playWhenReady = false
                true
            }
            prepareAsync()
        }
    }

    private class RenderThread(
        private val outputSurfaceTexture: SurfaceTexture,
        private val initialWidth: Int,
        private val initialHeight: Int,
        private val onDecoderSurfaceReady: (Surface) -> Unit
    ) : HandlerThread("PackedAlphaRenderThread"), SurfaceTexture.OnFrameAvailableListener {

        private val startedLatch = CountDownLatch(1)
        private lateinit var handler: Handler

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var decoderSurfaceTexture: SurfaceTexture? = null
        private var decoderSurface: Surface? = null

        private var program = 0
        private var textureId = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var texMatrixHandle = 0
        private var scaleHandle = 0
        private var textureHandle = 0

        private var viewportWidth = initialWidth.coerceAtLeast(1)
        private var viewportHeight = initialHeight.coerceAtLeast(1)
        private var contentWidth = 1
        private var contentHeight = 1
        private var scaleX = 1f
        private var scaleY = 1f
        private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
        private var frameAvailable = false

        private val triangleVertices: FloatBuffer = createFloatBuffer(
            floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
            )
        )

        override fun onLooperPrepared() {
            handler = Handler(looper)
            initGl()
            startedLatch.countDown()
        }

        fun updateViewport(width: Int, height: Int) {
            awaitStart()
            handler.post {
                viewportWidth = width.coerceAtLeast(1)
                viewportHeight = height.coerceAtLeast(1)
                updateScale()
                drawFrame()
            }
        }

        fun updateVideoSize(width: Int, height: Int) {
            awaitStart()
            handler.post {
                contentWidth = width.coerceAtLeast(1)
                contentHeight = height.coerceAtLeast(1)
                updateScale()
            }
        }

        fun release() {
            awaitStart()
            handler.post {
                releaseGl()
                quitSafely()
            }
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            if (!::handler.isInitialized) return
            handler.post {
                frameAvailable = true
                drawFrame()
            }
        }

        private fun awaitStart() {
            if (!isAlive) return
            startedLatch.await()
        }

        private fun initGl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val eglConfig = configs[0] ?: error("Unable to choose EGL config")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurfaceTexture, surfaceAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL surface" }
            makeCurrent()

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
            textureId = createExternalTexture()
            decoderSurfaceTexture = SurfaceTexture(textureId).also {
                it.setOnFrameAvailableListener(this, handler)
            }
            decoderSurface = Surface(decoderSurfaceTexture)
            onDecoderSurfaceReady(decoderSurface!!)

            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            drawFrame()
        }

        private fun drawFrame() {
            makeCurrent()
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            decoderSurfaceTexture?.let { surfaceTexture ->
                if (frameAvailable) {
                    frameAvailable = false
                    surfaceTexture.updateTexImage()
                    surfaceTexture.getTransformMatrix(texMatrix)
                }
            }

            GLES20.glUseProgram(program)
            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, triangleVertices)
            GLES20.glEnableVertexAttribArray(positionHandle)
            triangleVertices.position(2)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, triangleVertices)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
            GLES20.glUniform2f(scaleHandle, scaleX, scaleY)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureHandle, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun updateScale() {
            val viewAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
            val contentAspect = contentWidth.toFloat() / contentHeight.toFloat()
            if (viewAspect > contentAspect) {
                scaleX = contentAspect / viewAspect
                scaleY = 1f
            } else {
                scaleX = 1f
                scaleY = viewAspect / contentAspect
            }
        }

        private fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun releaseGl() {
            decoderSurfaceTexture?.setOnFrameAvailableListener(null)
            decoderSurface?.release()
            decoderSurface = null
            decoderSurfaceTexture?.release()
            decoderSurfaceTexture = null

            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
        }

        companion object {
            private const val STRIDE_BYTES = 4 * 4

            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uTexMatrix;
                uniform vec2 uScale;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = vec4(aPosition.xy * uScale, 0.0, 1.0);
                    vTexCoord = (uTexMatrix * aTexCoord).xy;
                }
            """

            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES uTexture;
                void main() {
                    vec2 packedUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
                    vec2 colorUv = vec2(packedUv.x * 0.5, packedUv.y);
                    vec2 alphaUv = vec2(0.5 + packedUv.x * 0.5, packedUv.y);
                    vec4 color = texture2D(uTexture, colorUv);
                    float alpha = texture2D(uTexture, alphaUv).r;
                    gl_FragColor = vec4(color.rgb, alpha);
                }
            """

            private fun createFloatBuffer(values: FloatArray): FloatBuffer {
                return ByteBuffer.allocateDirect(values.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }
            }

            private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
                val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
                val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
                return GLES20.glCreateProgram().also { program ->
                    GLES20.glAttachShader(program, vertexShader)
                    GLES20.glAttachShader(program, fragmentShader)
                    GLES20.glLinkProgram(program)
                    val linkStatus = IntArray(1)
                    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
                    check(linkStatus[0] == GLES20.GL_TRUE) {
                        GLES20.glGetProgramInfoLog(program) ?: "Unknown OpenGL link error"
                    }
                    GLES20.glDeleteShader(vertexShader)
                    GLES20.glDeleteShader(fragmentShader)
                }
            }

            private fun compileShader(type: Int, source: String): Int {
                return GLES20.glCreateShader(type).also { shader ->
                    GLES20.glShaderSource(shader, source)
                    GLES20.glCompileShader(shader)
                    val compileStatus = IntArray(1)
                    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
                    check(compileStatus[0] == GLES20.GL_TRUE) {
                        GLES20.glGetShaderInfoLog(shader) ?: "Unknown OpenGL compile error"
                    }
                }
            }

            private fun createExternalTexture(): Int {
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                return textures[0]
            }
        }
    }
}
