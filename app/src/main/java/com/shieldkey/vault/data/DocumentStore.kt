package com.shieldkey.vault.data

import android.content.Context
import com.shieldkey.vault.crypto.SkCrypto
import java.io.File
import java.util.UUID

/**
 * Stockage des DOCUMENTS (fichiers) du coffre.
 *
 * Chaque document est chiffré séparément (AES-256-GCM avec la DEK du coffre) dans son
 * propre fichier `filesDir/docs/<uuid>.blob`. Les métadonnées (nom, type, taille) vivent,
 * elles, dans le coffre JSON (voir [VaultRepository]).
 *
 * Pourquoi des fichiers séparés plutôt que tout dans vault.skv :
 *  - un document peut peser plusieurs Mo → inutile de déchiffrer/rechiffrer TOUT le coffre
 *    à chaque ajout ou lecture ;
 *  - le contenu reste 100 % hors-ligne et chiffré au repos, comme le reste.
 */
class DocumentStore(context: Context) {

    private val dir = File(context.filesDir, "docs").apply { if (!exists()) mkdirs() }

    private fun blob(id: String) = File(dir, "$id.blob")

    /** Chiffre [bytes] et l'enregistre. Renvoie l'identifiant unique du document. */
    fun save(dek: ByteArray, bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        blob(id).writeBytes(SkCrypto.encrypt(bytes, dek))
        return id
    }

    /** Déchiffre et renvoie le contenu du document [id]. */
    fun read(dek: ByteArray, id: String): ByteArray =
        SkCrypto.decrypt(blob(id).readBytes(), dek)

    /** Supprime définitivement le fichier chiffré du document [id]. */
    fun delete(id: String) {
        blob(id).delete()
    }
}
