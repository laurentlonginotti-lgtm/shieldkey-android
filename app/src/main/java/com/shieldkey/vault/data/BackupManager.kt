package com.shieldkey.vault.data

import android.content.Context
import android.util.Base64
import com.shieldkey.vault.crypto.SkCrypto
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

    /**
     * Restaure une sauvegarde dans `filesDir`. Renvoie true si OK (mot de passe bon + format valide).
     * ⚠️ ÉCRASE le coffre existant de cet appareil → à confirmer côté UI.
     */
    fun import(context: Context, fileBytes: ByteArray, password: String): Boolean {
        return try {
            val text = String(fileBytes, Charsets.UTF_8).trim()
            val nl = text.indexOf('\n')
            if (nl < 0 || text.substring(0, nl).trim() != MAGIC) return false
            val blob = unb64(text.substring(nl + 1).trim())
            if (blob.size <= SkCrypto.SALT_LEN) return false
            val salt = blob.copyOfRange(0, SkCrypto.SALT_LEN)
            val enc = blob.copyOfRange(SkCrypto.SALT_LEN, blob.size)
            val key = SkCrypto.deriveKey(password.toByteArray(Charsets.UTF_8), salt)
            val bundleBytes = SkCrypto.decrypt(enc, key)   // lève si mot de passe faux / fichier altéré

            val bundle = JSONObject(String(bundleBytes, Charsets.UTF_8))
            val filesDir = context.filesDir

            // coffre
            File(filesDir, "vault.skv").writeBytes(unb64(bundle.getString("vault")))

            // documents : on remplace complètement le dossier
            val docsDir = File(filesDir, "docs").apply { mkdirs() }
            docsDir.listFiles()?.forEach { it.delete() }
            bundle.optJSONObject("docs")?.let { docsObj ->
                for (name in docsObj.keys()) {
                    File(docsDir, name).writeBytes(unb64(docsObj.getString(name)))
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
