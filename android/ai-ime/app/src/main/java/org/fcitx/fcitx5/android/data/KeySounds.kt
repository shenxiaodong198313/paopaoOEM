package org.fcitx.fcitx5.android.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object KeySounds {
    @Volatile private var localSoundOverride: String? = null
    fun configureLocalSound(id: String?) {
        val next = id?.takeIf { candidate -> items.any { it.first == candidate } }
        if (localSoundOverride != next) { localSoundOverride = next; cancelPending() }
    }
    const val DEFAULT_ID = "bd_default"
    val items = listOf(
        "bd_default" to "默认",
        "standard" to "标准键音",
        "mouse" to "鼠标轻击",
        "click" to "短促轻点",
        "crisp" to "清脆轻敲",
        "pluck" to "轻拨弦音",
        "glass" to "玻璃清响",
        "tick" to "滴答滴答",
        "bd_allegro" to "轻快板音",
        "bd_bopu" to "啵卟啵卟",
        "bd_dadu" to "嗒嘟嗒嘟",
        "bd_dilu" to "嘀噜嘀噜",
        "bd_duda" to "嘟哒嘟哒",
        "bd_dulu" to "嘟噜嘟噜",
        "bd_dudu" to "嘟嘟嘟嘟",
        "bd_kada" to "咔哒咔哒",
        "bd_koukou" to "扣扣扣扣",
        "bd_mechanical" to "机械敲击",
        "bd_blackberry" to "黑莓键音",
        "bd_miku" to "初音轻唱",
        "bd_marimba" to "马林巴音",
        "bd_cute" to "俏皮可爱",
        "bd_puppy" to "奶汪轻叫",
        "bd_kitten" to "奶猫轻喵",
        "bd_pikachu" to "皮卡轻鸣",
        "bd_cotton_candy" to "软绵糖音",
        "bd_pop" to "轻巧弹出",
        "bd_cartoon_pop" to "卡通弹跳",
        "bd_cute_simple" to "简约萌音",
        "bd_cute_toy" to "萌趣玩具",
    )
    private val lock = ReentrantLock()
    private val preparationGeneration = AtomicLong()
    private val touchSequence = AtomicLong()
    class ActionTicket internal constructor(internal val generation: Long, internal val sequence: Long,
        internal val requestedAt: Long, internal val soundId: Int?) {
        internal val consumed = AtomicBoolean()
    }
    private data class ActionPlayback(val context: Context, val ticket: ActionTicket, val volume: Float)
    private val queuedAction = AtomicReference<ActionPlayback?>()
    private val actionWorkerScheduled = AtomicBoolean()
    fun beginTouch() { touchSequence.incrementAndGet() }
    private val preparationExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "key-sound-loader").apply { isDaemon = true }
    }
    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private data class Preview(
        val context: Context, val id: String, val gain: Float,
        val requestedAt: Long, val generation: Long, val sequence: Long
    )
    private val previewSequence = AtomicLong()
    private val queuedPreview = AtomicReference<Preview?>()
    private val previewWorkerScheduled = AtomicBoolean()
    private var pending: Preview? = null
    fun cancelPending() {
        beginTouch()
        // Invalidate immediately, even while the loader owns the audio lock.
        previewSequence.incrementAndGet()
        queuedPreview.set(null)
        queuedAction.set(null)
    }
    fun release() {
        // A queued preload from before memory pressure must not recreate the pool.
        preparationGeneration.incrementAndGet()
        cancelPending()
        lock.withLock {
            pending = null
            pool?.release(); pool = null; loaded.clear(); ready.clear()
        }
    }
    fun selected(context: Context): String = context.getSharedPreferences("paopao_key_sounds", Context.MODE_PRIVATE)
        .getString("selected", DEFAULT_ID).takeIf { id -> items.any { it.first == id } } ?: DEFAULT_ID
    fun select(context: Context, id: String) {
        require(items.any { it.first == id })
        context.getSharedPreferences("paopao_key_sounds", Context.MODE_PRIVATE).edit().putString("selected", id).apply()
    }
    fun prepareAsync(context: Context) {
        val generation = preparationGeneration.get()
        val application = context.applicationContext
        preparationExecutor.execute {
            runCatching {
                lock.withLock {
                    if (generation == preparationGeneration.get()) prepareLocked(application)
                }
            }
        }
    }
    fun prepare(context: Context) = lock.withLock { prepareLocked(context.applicationContext) }
    private fun prepareLocked(context: Context) {
        if (pool != null) return
        // Warm the preference file before a touch can use the prepared pool.
        selected(context)
        val player = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        pool = player
        player.setOnLoadCompleteListener { _, soundId, status ->
            // SoundPool may deliver this on the main looper; never wait there
            // for the loader to finish opening the remaining assets.
            preparationExecutor.execute {
                lock.withLock {
                    if (player !== pool) return@withLock
                    if (status == 0) ready.add(soundId)
                    pending?.let { request -> if (loaded[request.id] == soundId) {
                        pending = null
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        if (status == 0 && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && isCurrent(request)) {
                            player.play(soundId, request.gain, request.gain, 1, 0, 1f)
                        }
                    } }
                }
            }
        }
        try {
            items.forEach { (id, _) -> context.assets.openFd("key_sounds/$id.wav").use { loaded[id] = player.load(it, 1) } }
        } catch (error: Exception) {
            player.release()
            pool = null
            loaded.clear()
            ready.clear()
            throw error
        }
    }
    // Typing never waits behind loading or replays a tap after its key has lifted.
    private fun readySound(context: Context, skinCandidates: List<String>): Int? {
        localSoundOverride?.let { return loaded[it]?.takeIf { sound -> sound in ready } }
        val explicit = context.getSharedPreferences("paopao_key_sounds", Context.MODE_PRIVATE).contains("selected")
        val candidates = if (explicit || skinCandidates.isEmpty()) listOf(selected(context)) else skinCandidates
        return candidates.firstNotNullOfOrNull { id -> loaded[id]?.takeIf { it in ready } }
    }
    // Click-only actions capture readiness now, then acknowledge success without replaying cold input.
    fun captureAction(context: Context, skinCandidates: List<String>, enabled: Boolean = true): ActionTicket {
        val sequence = touchSequence.incrementAndGet()
        val generation = preparationGeneration.get()
        val requestedAt = android.os.SystemClock.uptimeMillis()
        val soundId = if (enabled && lock.tryLock()) try { if (pool != null) readySound(context, skinCandidates) else null } finally { lock.unlock() } else null
        return ActionTicket(generation, sequence, requestedAt, soundId)
    }
    private fun current(ticket: ActionTicket): Boolean = ticket.sequence == touchSequence.get() &&
        ticket.generation == preparationGeneration.get() &&
        android.os.SystemClock.uptimeMillis() - ticket.requestedAt in 0 until 200
    // Acknowledgement never performs a system audio query on the candidate/toolbar caller.
    // One latest slot bounds work even when the loader or an older mute query is slow.
    fun queueAction(context: Context, ticket: ActionTicket, volume: Float) {
        if (ticket.consumed.get()) return
        if (volume <= 0 || ticket.soundId == null || !current(ticket)) { ticket.consumed.set(true); return }
        val request = ActionPlayback(context.applicationContext, ticket, volume)
        queuedAction.updateAndGet { previous -> if (current(ticket)) request else previous }
        scheduleAction()
    }
    private fun scheduleAction() {
        if (queuedAction.get() == null || !actionWorkerScheduled.compareAndSet(false, true)) return
        preparationExecutor.execute {
            try {
                val request = queuedAction.getAndSet(null) ?: return@execute
                runCatching { playAction(request.context, request.ticket, request.volume) }
            } finally {
                actionWorkerScheduled.set(false)
                scheduleAction()
            }
        }
    }
    fun playAction(context: Context, ticket: ActionTicket, volume: Float) {
        if (!ticket.consumed.compareAndSet(false, true) || volume <= 0 || !current(ticket) || !lock.tryLock()) return
        try {
            val player = pool ?: return
            val soundId = ticket.soundId?.takeIf { it in ready } ?: return
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || !current(ticket)) return
            val gain = volume.coerceIn(0f, 1f)
            player.play(soundId, gain, gain, 1, 0, 1f)
        } finally { lock.unlock() }
    }
    fun playTouch(context: Context, volume: Float, skinCandidates: List<String> = emptyList()) {
        if (volume <= 0 || !lock.tryLock()) return
        try {
            val player = pool ?: return
            // Presence matters: even an obsolete explicit ID retains the old bd_default fallback.
            val soundId = readySound(context, skinCandidates) ?: return
            if (soundId !in ready) return
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val gain = volume.coerceIn(0f, 1f)
            player.play(soundId, gain, gain, 1, 0, 1f)
        } finally {
            lock.unlock()
        }
    }
    private fun isCurrent(request: Preview): Boolean =
        request.sequence == previewSequence.get() &&
            request.generation == preparationGeneration.get() &&
            android.os.SystemClock.uptimeMillis() - request.requestedAt in 0 until 200

    // A bounded latest-selection slot keeps settings taps off the audio/loading lock.
    // The 200 ms deadline starts at the tap, not after queued work or cold loading.
    fun play(context: Context, id: String = selected(context), volume: Float) {
        val sequence = previewSequence.incrementAndGet()
        val request = if (volume > 0 && items.any { it.first == id }) Preview(
            context.applicationContext, id, volume.coerceIn(0f, 1f),
            android.os.SystemClock.uptimeMillis(), preparationGeneration.get(), sequence
        ) else null
        queuedPreview.updateAndGet { previous ->
            if (sequence == previewSequence.get()) request else previous
        }
        schedulePreview()
    }

    private fun schedulePreview() {
        if (queuedPreview.get() == null || !previewWorkerScheduled.compareAndSet(false, true)) return
        preparationExecutor.execute {
            try {
                val request = queuedPreview.getAndSet(null) ?: return@execute
                runCatching {
                    lock.withLock {
                        if (!isCurrent(request)) return@withLock
                        pending = null
                        val audio = request.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || !isCurrent(request)) return@withLock
                        prepareLocked(request.context)
                        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || !isCurrent(request)) return@withLock
                        val soundId = loaded[request.id] ?: return@withLock
                        if (soundId in ready) pool?.play(soundId, request.gain, request.gain, 1, 0, 1f)
                        else pending = request
                    }
                }
            } finally {
                previewWorkerScheduled.set(false)
                schedulePreview()
            }
        }
    }
}
