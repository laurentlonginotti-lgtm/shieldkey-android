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
import android.util.Base64
import com.shieldkey.vault.crypto.SkCrypto
import com.shieldkey.vault.util.AtomicWrite
import org.json.JSONObject
import java.io.File

/**
 * Sauvegarde / restauration du coffre dans UN fichier `.skb` portable et chiffré — pour
 * changer de téléphone sans rien perdre.
 *
 * Contenu empaqueté : le fichier du coffre (`vault.skv`, déjà chiffré par le mot de passe
 * maître) + tous les documents chiffrés. Le paquet est ensuite chiffré une **2ᵉ fois** avec une
 * clé dérivée (Argon2id) du **mot de passe maître** :
 *  - le fichier ne révèle RIEN au repos (pas même la structure ou les sels) ;
 *  - le **même mot de passe maître** rouvre le coffre sur le nouveau téléphone — inutile de
 *    stocker le mot de passe en clair, il « revient » via `vault.skv`.
 */
object BackupManager {

    private const val MAGIC = "SHIELDKEY-BACKUP-1"

    /** Construit le contenu (texte) d'une sauvegarde chiffrée. [password] = mot de passe maître. */
    fun export(context: Context, password: String): ByteArray {
        val filesDir = context.filesDir
        val vault = File(filesDir, "vault.skv")
        require(vault.exists()) { "coffre introuvable" }

        // 1. paquet en clair : vault.skv + documents, en JSON base64
        val docsObj = JSONObject()
        File(filesDir, "docs").listFiles()?.forEach { f ->
            if (f.isFile) docsObj.put(f.name, b64(f.readBytes()))
        }
        val bundle = JSONObject()
            .put("v", 1)
            .put("vault", b64(vault.readBytes()))
            .put("docs", docsObj)
        val bundleBytes = bundle.toString().toByteArray(Charsets.UTF_8)

        // 2. couche externe : Argon2id(mot de passe) + AES-256-GCM
        val salt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val key = SkCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), salt)
        val blob = salt + SkCrypto.encrypt(bundleBytes, key)

        // 3. fichier texte portable (magic + base64)
        return (MAGIC + "\n" + b64(blob)).toByteArray(Charsets.UTF_8)
    }

    /** Nom de blob acceptable : `<uuid>.blob` (pas de séparateur, pas de `..`) — même si le paquet
     *  est authentifié (GCM), on ne laisse pas un nom piloter un chemin sur le disque. */
    private val SAFE_BLOB_NAME = Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*")

    /**
     * Restaure une sauvegarde dans `filesDir`. Renvoie true si OK (mot de passe bon + format valide).
     * ⚠️ ÉCRASE le coffre existant de cet appareil → à confirmer côté UI.
     *
     * Robustesse : on ne touche PAS au disque avant d'avoir tout décodé et validé en mémoire
     * (phase 1). Puis on écrit dans un ordre qui ne laisse jamais un état inutilisable (phase 2) :
     *  1. les documents d'abord (nouveaux fichiers, ids uniques → n'écrasent rien d'utile) ;
     *  2. le coffre, en une écriture atomique ;
     *  3. seulement ensuite, les anciens blobs devenus orphelins.
     * Une interruption à n'importe quelle étape laisse SOIT l'ancien coffre complet, SOIT le
     * nouveau complet — au pire quelques blobs orphelins qui ne coûtent que de l'espace disque.
     */
    fun import(context: Context, fileBytes: ByteArray, password: String): Boolean {
        // --- Phase 1 : tout décoder / valider en mémoire, sans rien écrire.
        val decoded = decode(fileBytes, password) ?: return false

        // --- Phase 2 : écriture ordonnée (documents → coffre → nettoyage).
        val filesDir = context.filesDir
        val docsDir = File(filesDir, "docs").apply { mkdirs() }
        val before = docsDir.list()?.toSet() ?: emptySet()
        return try {
            decoded.docs.forEach { (name, bytes) -> AtomicWrite.write(File(docsDir, name), bytes) }
            AtomicWrite.write(File(filesDir, "vault.skv"), decoded.vault)
            docsDir.listFiles()?.forEach { if (it.name !in decoded.docs) it.delete() }
            true
        } catch (e: Exception) {
            // Une exception ici signifie que le coffre n'a PAS été remplacé (écriture atomique) :
            // on retire les blobs ajoutés par cette tentative et l'ancien état reste intact.
            decoded.docs.keys.filter { it !in before }.forEach { File(docsDir, it).delete() }
            false
        }
    }

    /** Contenu d'une sauvegarde entièrement décodée en mémoire. */
    private class Decoded(val vault: ByteArray, val docs: Map<String, ByteArray>)

    /** Déchiffre et valide le paquet. Renvoie null si mot de passe faux ou format invalide. */
    private fun decode(fileBytes: ByteArray, password: String): Decoded? = try {
        val text = String(fileBytes, Charsets.UTF_8).trim()
        val nl = text.indexOf('\n')
        if (nl < 0 || text.substring(0, nl).trim() != MAGIC) throw IllegalArgumentException("magic")
        val blob = unb64(text.substring(nl + 1).trim())
        if (blob.size <= SkCrypto.SALT_LEN) throw IllegalArgumentException("taille")
        val salt = blob.copyOfRange(0, SkCrypto.SALT_LEN)
        val enc = blob.copyOfRange(SkCrypto.SALT_LEN, blob.size)
        val key = SkCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), salt)
        val bundleBytes = SkCrypto.decrypt(enc, key)   // lève si mot de passe faux / fichier altéré

        val bundle = JSONObject(String(bundleBytes, Charsets.UTF_8))
        val vaultBytes = unb64(bundle.getString("vault"))
        // Le coffre restauré doit lui-même être un vault.skv cohérent (sinon on refuse AVANT d'écraser).
        JSONObject(String(vaultBytes, Charsets.UTF_8)).getString("masterWrap")

        val docs = LinkedHashMap<String, ByteArray>()
        bundle.optJSONObject("docs")?.let { docsObj ->
            for (name in docsObj.keys()) {
                if (!SAFE_BLOB_NAME.matches(name)) throw IllegalArgumentException("nom de blob")
                docs[name] = unb64(docsObj.getString(name))
            }
        }
        Decoded(vaultBytes, docs)
    } catch (e: Exception) {
        null
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
