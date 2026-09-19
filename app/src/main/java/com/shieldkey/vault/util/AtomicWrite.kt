package com.shieldkey.vault.util

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Écriture ATOMIQUE et DURABLE d'un fichier.
 *
 * Pourquoi : `File.writeBytes()` écrase le fichier en place. Si l'app est tuée ou la batterie
 * lâche PENDANT l'écriture, le fichier est tronqué. Pour `vault.skv` (sels + enveloppes de la
 * DEK + données dans un seul fichier), ce serait la perte totale du coffre — et le kit de
 * secours n'y pourrait rien, puisqu'il déverrouille un fichier qui n'existe plus.
 *
 * Mécanisme (le classique « write-temp / fsync / rename ») :
 *  1. on écrit tout dans `<nom>.tmp` ;
 *  2. `fsync` : on force le noyau à poser les octets sur la mémoire flash (sans ça, ils
 *     peuvent rester en cache d'écriture et disparaître à la coupure) ;
 *  3. `rename(tmp → nom)` : opération atomique du système de fichiers. À tout instant il
 *     existe SOIT l'ancien fichier complet, SOIT le nouveau complet — jamais un demi-fichier ;
 *  4. `fsync` du dossier parent, pour que le renommage lui-même soit durable.
 *
 * Un `.tmp` résiduel (interruption entre 1 et 3) est simplement ignoré par les lecteurs, qui
 * n'ouvrent que le fichier final ; il est écrasé à la prochaine écriture.
 */
object AtomicWrite {

    fun write(target: File, bytes: ByteArray) {
        val dir = target.absoluteFile.parentFile
            ?: throw IllegalArgumentException("fichier sans dossier parent : $target")
        val tmp = File(dir, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            // ATOMIC_MOVE = rename(2) : sur Linux/Android, remplace la cible existante d'un coup.
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            syncDir(dir)
        } finally {
            // Sans effet si le renommage a réussi ; nettoie le .tmp si une étape a échoué.
            tmp.delete()
        }
    }

    /** fsync d'un dossier : best-effort (certains systèmes de fichiers le refusent, sans gravité). */
    private fun syncDir(dir: File) {
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) { /* le rename est déjà effectif ; seule la durabilité immédiate est best-effort */ }
    }
}
