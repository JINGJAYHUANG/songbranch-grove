package dev.songbranch.grove.content

import android.content.Context
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import songbranch.audio.Tones
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Birdsong synthesized in code (see songbranch.audio.Tones), written once as small WAV files in the app's
 * private storage and played from emitters placed where the birds wake up. No audio assets ship with the app.
 * Audio is optional: if anything fails, the game simply stays silent.
 */
class GroveAudio(private val context: Context) {
    private class Emitter(val entity: Entity, val resource: AudioResource, val controller: AudioPlayerController)

    private val emitters = ArrayList<Emitter>()
    private var chord: Emitter? = null
    var muted = false

    /** Step count of the pentatonic tones: wave i uses tone i (clamped to the last one). */
    val steps = 8

    suspend fun prepare(parent: Entity) {
        val dir = File(context.filesDir, "songbranch-audio").apply { mkdirs() }
        val files = withContext(Dispatchers.IO) {
            val list = (0 until steps).map { i ->
                File(dir, "tone_$i.wav").also { f -> if (!f.exists()) writeWav(f, Tones.chirp(Tones.pentatonicHz(i), 260, 0.55)) }
            }
            val chordFile = File(dir, "chord.wav").also { f -> if (!f.exists()) writeWav(f, Tones.chord(900, 0.6)) }
            list + chordFile
        }
        try {
            files.forEachIndexed { i, f ->
                val resource = AudioResource.load("songbranch_tone_$i", f.absolutePath, LoadType.FROM_STORAGE)
                val entity = Entity()
                entity.components.set(ObjectAudioComponent())
                parent.addChild(entity)
                val controller = entity.prepareAudio(resource)
                val emitter = Emitter(entity, resource, controller)
                if (i < steps) emitters.add(emitter) else chord = emitter
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Audio unavailable, continuing without sound", t)
        }
    }

    /** Plays the tone for dawn wave [wave] from [position]. */
    fun playWave(wave: Int, position: Vector3) {
        if (muted || emitters.isEmpty()) return
        val e = emitters[wave.coerceIn(0, emitters.size - 1)]
        play(e, position)
    }

    fun playChord(position: Vector3) {
        if (muted) return
        chord?.let { play(it, position) }
    }

    private fun play(e: Emitter, position: Vector3) {
        try {
            e.entity.components[TransformComponent::class.java]?.position = position
            e.controller.stop()
            e.controller.play()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not play a tone", t)
        }
    }

    fun release() {
        (emitters + listOfNotNull(chord)).forEach { e ->
            try {
                e.controller.stop(); e.controller.close(); e.resource.close()
            } catch (_: Throwable) {
            }
        }
        emitters.clear(); chord = null
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        bb.putInt(Tones.SAMPLE_RATE).putInt(Tones.SAMPLE_RATE * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataBytes)
        for (s in pcm) bb.putShort(s)
        file.writeBytes(bb.array())
    }

    companion object { private const val TAG = "SongbranchAudio" }
}
