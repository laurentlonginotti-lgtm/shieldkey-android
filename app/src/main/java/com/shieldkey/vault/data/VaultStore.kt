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
import com.shieldkey.vault.crypto.RecoveryCode
import com.shieldkey.vault.crypto.SkCrypto
import com.shieldkey.vault.util.AtomicWrite
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stockage chiffré du coffre, avec chiffrement à enveloppe ("envelope encryption") :
 *
 *  - DEK (Data Encryption Key) aléatoire = la clé qui chiffre réellement le coffre.
 *  - La DEK est emballée DEUX fois :
 *      • avec la clé dérivée du MOT DE PASSE MAÎTRE  (déverrouillage normal)
 *      • avec la clé dérivée du CODE DE SECOURS      (récupération)
 *
 * Conséquence : on peut récupérer le coffre via le code de secours SANS jamais
 * stocker le mot de passe maître nulle part = aucune porte dérobée.
 *
 * Fichier : filesDir/vault.skv (stockage interne privé, non sauvegardé sur le cloud).
 * Toute écriture passe par [AtomicWrite] : un crash ou une coupure en pleine écriture ne peut
 * pas laisser un fichier tronqué (= coffre irrécupérable).
 */
class VaultStore(context: Context) {

    private val file = File(context.filesDir, "vault.skv")

    fun isInitialized(): Boolean = file.exists()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    /** Seul point d'écriture du coffre : atomique + durable. */
    private fun save(root: JSONObject) =
        AtomicWrite.write(file, root.toString().toByteArray(Charsets.UTF_8))

    data class CreateResult(val dek: ByteArray, val recoveryCode: String)

    /** Crée un coffre vide. Renvoie la DEK déverrouillée + le code de secours à afficher UNE fois. */
    fun create(masterPassword: String): CreateResult {
        val masterSalt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val recoverySalt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val dek = SkCrypto.randomBytes(SkCrypto.KEY_LEN)
        val recoveryCode = RecoveryCode.generate()

        val masterKey = SkCrypto.deriveKey(masterPassword.toByteArray(Charsets.UTF_8), masterSalt)
        val recoveryKey = SkCrypto.deriveKey(RecoveryCode.normalize(recoveryCode), recoverySalt)

        val emptyVault = JSONObject()
            .put("version", 1)
            .put("items", JSONArray())
            .toString()

        val root = JSONObject().apply {
            put("version", 1)
            put("masterSalt", b64(masterSalt))
            put("recoverySalt", b64(recoverySalt))
            put("masterWrap", b64(SkCrypto.encrypt(dek, masterKey)))
            put("recoveryWrap", b64(SkCrypto.encrypt(dek, recoveryKey)))
            put("vault", b64(SkCrypto.encrypt(emptyVault.toByteArray(Charsets.UTF_8), dek)))
        }
        save(root)
        return CreateResult(dek, recoveryCode)
    }

    /** Déverrouille avec le mot de passe maître. Renvoie la DEK, ou null si le mot de passe est faux. */
    fun unlock(masterPassword: String): ByteArray? {
        val root = JSONObject(file.readText())
        val masterKey = SkCrypto.deriveKey(
            masterPassword.toByteArray(Charsets.UTF_8),
            unb64(root.getString("masterSalt"))
        )
        return try {
            SkCrypto.decrypt(unb64(root.getString("masterWrap")), masterKey)
        } catch (e: Exception) {
            null
        }
    }

    /** Récupération : déverrouille via le code de secours. Renvoie la DEK, ou null si le code est faux. */
    fun unlockWithRecovery(recoveryCode: String): ByteArray? {
        val root = JSONObject(file.readText())
        val recoveryKey = SkCrypto.deriveKey(
            RecoveryCode.normalize(recoveryCode),
            unb64(root.getString("recoverySalt"))
        )
        return try {
            SkCrypto.decrypt(unb64(root.getString("recoveryWrap")), recoveryKey)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Après une récupération réussie : redéfinit le mot de passe maître à partir de la DEK,
     * ET renouvelle le code de secours. Renvoie le nouveau code — l'appelant DOIT l'afficher.
     *
     * Renouveler n'est pas un détail de confort. L'ancien code de secours est une clé complète
     * du coffre : il emballe la même DEK, au même titre que le mot de passe maître. Le laisser
     * en place reviendrait à ne changer qu'une serrure sur deux — et précisément dans le moment
     * où l'utilisateur change de mot de passe, souvent parce qu'il se croit compromis. Quelqu'un
     * qui aurait vu ou photographié l'ancienne feuille garderait alors un accès à vie, sans que
     * rien dans l'app ne le laisse soupçonner.
     *
     * On réemballe donc la DEK avec un code neuf et un sel neuf : l'ancien code ne déchiffre
     * plus rien. Le contenu du coffre, lui, n'est pas touché (la DEK ne change pas).
     */
    fun resetMasterPassword(dek: ByteArray, newPassword: String): String {
        val root = JSONObject(file.readText())

        val masterSalt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val masterKey = SkCrypto.deriveKey(newPassword.toByteArray(Charsets.UTF_8), masterSalt)
        root.put("masterSalt", b64(masterSalt))
        root.put("masterWrap", b64(SkCrypto.encrypt(dek, masterKey)))

        val recoveryCode = RecoveryCode.generate()
        val recoverySalt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val recoveryKey = SkCrypto.deriveKey(RecoveryCode.normalize(recoveryCode), recoverySalt)
        root.put("recoverySalt", b64(recoverySalt))
        root.put("recoveryWrap", b64(SkCrypto.encrypt(dek, recoveryKey)))

        save(root)
        return recoveryCode
    }

    /** Lit le contenu déchiffré du coffre (JSON) avec la DEK. */
    fun readVault(dek: ByteArray): String {
        val root = JSONObject(file.readText())
        return String(SkCrypto.decrypt(unb64(root.getString("vault")), dek), Charsets.UTF_8)
    }

    /** Écrit (chiffre) le contenu du coffre avec la DEK. */
    fun writeVault(dek: ByteArray, vaultJson: String) {
        val root = JSONObject(file.readText())
        root.put("vault", b64(SkCrypto.encrypt(vaultJson.toByteArray(Charsets.UTF_8), dek)))
        save(root)
    }
}
