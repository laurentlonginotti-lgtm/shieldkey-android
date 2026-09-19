/*
 * ShieldKey — coffre-fort numérique 100 % hors-ligne
 * Copyright (C) 2026 Laurent Longinotti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shieldkey.vault.data

import android.content.Context
import com.shieldkey.vault.crypto.SkCrypto
import com.shieldkey.vault.util.AtomicWrite
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

    /** Chiffre [bytes] et l'enregistre (écriture atomique : jamais de blob tronqué référencé
     *  par le coffre). Renvoie l'identifiant unique du document. */
    fun save(dek: ByteArray, bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        AtomicWrite.write(blob(id), SkCrypto.encrypt(bytes, dek))
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
