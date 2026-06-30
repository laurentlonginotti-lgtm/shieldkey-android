package com.shieldkey.vault.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Effets sonores de ShieldKey, synthétisés à la volée (aucun fichier audio,
 * aucune permission). Chaque son est une courte séquence de notes avec une
 * enveloppe douce (pas de "clic"). Réglable facilement (fréquences / durées).
 */
object SoundFx {

    /** Activé par défaut ; sera relié à un réglage utilisateur plus tard. */
    @Volatile
    var enabled: Boolean = true

    private const val SR = 44100   // fréquence d'échantillonnage

    private data class Note(val freq: Double, val ms: Int, val vol: Double = 0.5)

    // --- Palette sonore ---
    fun open()    = play(listOf(Note(587.33, 90), Note(784.0, 90), Note(1174.66, 170)))   // montant : accueil
    fun close()   = play(listOf(Note(784.0, 90), Note(587.33, 90), Note(440.0, 160)))     // descendant : verrouillage
    fun success() = play(listOf(Note(880.0, 80), Note(1318.51, 150)))                      // confirmation
    fun inject()  = play(listOf(Note(988.0, 55, 0.45), Note(1318.51, 80, 0.45)))           // double bip : remplissage OK
    fun error()   = play(listOf(Note(311.13, 150, 0.55), Note(233.08, 260, 0.55)))         // grave descendant : alerte

    private fun play(seq: List<Note>) {
        if (!enabled) return
        thread(isDaemon = true) {
            var track: AudioTrack? = null
            try {
                val pcm = render(seq)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((pcm.size * 1000L / SR) + 150)
            } catch (_: Exception) {
                // un souci audio ne doit jamais faire planter l'app
            } finally {
                try { track?.release() } catch (_: Exception) {}
            }
        }
    }

    private fun render(seq: List<Note>): ShortArray {
        val total = seq.sumOf { it.ms * SR / 1000 }
        val out = ShortArray(total)
        var idx = 0
        for (n in seq) {
            val samples = n.ms * SR / 1000
            for (i in 0 until samples) {
                val t = i.toDouble() / SR
                val env = envelope(i, samples)
                // fondamentale + une octave atténuée (timbre plus chaud)
                val w = sin(2 * PI * n.freq * t) + 0.3 * sin(2 * PI * n.freq * 2 * t)
                out[idx++] = ((w / 1.3) * n.vol * env * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** Enveloppe attaque/relâche (évite les "clics" en début/fin de note). */
    private fun envelope(i: Int, n: Int): Double {
        val a = (n * 0.15).toInt().coerceAtLeast(1)
        val r = (n * 0.30).toInt().coerceAtLeast(1)
        return when {
            i < a -> i.toDouble() / a
            i > n - r -> (n - i).toDouble() / r
            else -> 1.0
        }
    }
}
