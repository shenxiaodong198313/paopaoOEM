package org.fcitx.fcitx5.android.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

object KeySounds {
    val items = listOf("standard" to "标准按键","mouse" to "鼠标轻点","click" to "短促点击","crisp" to "清脆点击","pluck" to "拨弦","glass" to "玻璃轻响","tick" to "滴答")
    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private var pending: Pair<String, Float>? = null
    fun selected(context: Context): String = context.getSharedPreferences("paopao_key_sounds", Context.MODE_PRIVATE)
        .getString("selected", "standard").takeIf { id -> items.any { it.first == id } } ?: "standard"
    fun select(context: Context, id: String) {
        require(items.any { it.first == id })
        context.getSharedPreferences("paopao_key_sounds", Context.MODE_PRIVATE).edit().putString("selected", id).apply()
    }
    @Synchronized fun prepare(context: Context) {
        if (pool != null) return
        val player = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        pool = player
        player.setOnLoadCompleteListener { _, soundId, status -> synchronized(this) {
            if (status == 0) ready.add(soundId)
            pending?.let { (id, volume) -> if (loaded[id] == soundId && status == 0) {
                pending = null
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) player.play(soundId, volume, volume, 1, 0, 1f)
            } }
        } }
        items.forEach { (id, _) -> context.assets.openFd("key_sounds/$id.wav").use { loaded[id] = player.load(it, 1) } }
    }
    @Synchronized fun play(context: Context, id: String = selected(context), volume: Float) {
        if (volume <= 0 || items.none { it.first == id }) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        prepare(context.applicationContext)
        val soundId = loaded[id] ?: return
        val gain = volume.coerceIn(0f, 1f)
        if (soundId in ready) pool?.play(soundId, gain, gain, 1, 0, 1f) else pending = id to gain
    }
}
