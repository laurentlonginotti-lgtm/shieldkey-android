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

    /**
     * Emballe la DEK avec une clé dérivée du mot de passe maître, sur un sel neuf.
     * Seul point où le mot de passe maître touche le fichier : création, récupération, changement.
     */
    private fun wrapMaster(root: JSONObject, dek: ByteArray, masterPassword: String) {
        val salt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val key = SkCrypto.deriveKey(masterPassword.toByteArray(Charsets.UTF_8), salt)
        root.put("masterSalt", b64(salt))
        root.put("masterWrap", b64(SkCrypto.encrypt(dek, key)))
        // La clé dérivée a fini son office : seule la DEK doit survivre en mémoire.
        key.fill(0)
    }

    /** Emballe la DEK avec un code de secours NEUF (sel neuf) et renvoie ce code. */
    private fun wrapRecovery(root: JSONObject, dek: ByteArray): String {
        val code = RecoveryCode.generate()
        val salt = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val key = SkCrypto.deriveKey(RecoveryCode.normalize(code), salt)
        root.put("recoverySalt", b64(salt))
        root.put("recoveryWrap", b64(SkCrypto.encrypt(dek, key)))
        key.fill(0)
        return code
    }

    /** Crée un coffre vide. Renvoie la DEK déverrouillée + le code de secours à afficher UNE fois. */
    fun create(masterPassword: String): CreateResult {
        val dek = SkCrypto.randomBytes(SkCrypto.KEY_LEN)

        val emptyVault = JSONObject()
            .put("version", 1)
            .put("items", JSONArray())
            .toString()

        val root = JSONObject().put("version", 1)
        wrapMaster(root, dek, masterPassword)
        val recoveryCode = wrapRecovery(root, dek)
        root.put("vault", b64(SkCrypto.encrypt(emptyVault.toByteArray(Charsets.UTF_8), dek)))
        save(root)
        return CreateResult(dek, recoveryCode)
    }

    /** Issue d'une tentative de déverrouillage. */
    sealed class UnlockResult {
        class Success(val dek: ByteArray) : UnlockResult()
        object WrongPassword : UnlockResult()
        /** Trop d'essais : la saisie est refusée pendant [secondsLeft] secondes. */
        class Throttled(val secondsLeft: Long) : UnlockResult()
    }

    /**
     * Freinage des essais répétés.
     *
     * Argon2id impose déjà ~1 s par tentative, ce qui décourage un curieux mais pas quelqu'un
     * qui a le téléphone en main et du temps devant lui — un proche, typiquement, qui connaît
     * les habitudes et peut tester les candidats les plus probables. Au-delà de [FREE_TRIES]
     * échecs, on impose une attente qui double à chaque fois, plafonnée à [MAX_DELAY_S].
     *
     * Ce que cela ne fait PAS, et il faut être clair : un attaquant qui a extrait le fichier
     * (téléphone rooté, ou sauvegarde `.skb` récupérée) attaque hors ligne et n'est pas concerné.
     * Là, seule la force du mot de passe compte. Ce freinage protège l'accès PAR l'application.
     *
     * On ne détruit jamais le coffre après N échecs : une fausse manœuvre ou un enfant qui
     * tapote ne doit pas provoquer une perte de données irréversible.
     */
    private companion object {
        const val FREE_TRIES = 5
        const val BASE_DELAY_S = 15L
        const val MAX_DELAY_S = 900L    // 15 minutes
    }

    private fun penaltySeconds(fails: Int): Long {
        if (fails < FREE_TRIES) return 0
        val steps = (fails - FREE_TRIES).coerceAtMost(8)
        return (BASE_DELAY_S shl steps).coerceAtMost(MAX_DELAY_S)
    }

    /** Déverrouille avec le mot de passe maître. */
    fun unlock(masterPassword: String): UnlockResult {
        val root = JSONObject(file.readText())

        val lockedUntil = root.optLong("lockedUntil", 0L)
        val now = System.currentTimeMillis()
        if (lockedUntil > now) {
            return UnlockResult.Throttled((lockedUntil - now + 999) / 1000)
        }

        val masterKey = SkCrypto.deriveKey(
            masterPassword.toByteArray(Charsets.UTF_8),
            unb64(root.getString("masterSalt"))
        )
        val dek = try {
            SkCrypto.decrypt(unb64(root.getString("masterWrap")), masterKey)
        } catch (e: Exception) {
            null
        } finally {
            // La clé dérivée ne sert qu'ici : on ne la laisse pas traîner dans le tas.
            masterKey.fill(0)
        }

        return if (dek != null) {
            if (root.optInt("failCount", 0) != 0 || lockedUntil != 0L) {
                root.put("failCount", 0).put("lockedUntil", 0L)
                save(root)
            }
            UnlockResult.Success(dek)
        } else {
            val fails = root.optInt("failCount", 0) + 1
            val penalty = penaltySeconds(fails)
            root.put("failCount", fails)
                .put("lockedUntil", if (penalty > 0) now + penalty * 1000 else 0L)
            save(root)
            if (penalty > 0) UnlockResult.Throttled(penalty) else UnlockResult.WrongPassword
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
        } finally {
            recoveryKey.fill(0)
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
        wrapMaster(root, dek, newPassword)
        val recoveryCode = wrapRecovery(root, dek)
        // Un mot de passe redéfini remet aussi le compteur d'essais à zéro.
        root.put("failCount", 0).put("lockedUntil", 0L)
        save(root)
        return recoveryCode
    }

    /**
     * Changement VOLONTAIRE du mot de passe maître, coffre ouvert — l'appelant a déjà vérifié
     * le mot de passe actuel via [unlock], et donc traversé le freinage des essais.
     *
     * Seul l'emballage de la DEK est refait : le contenu du coffre, chiffré par la DEK, n'est pas
     * touché, et l'opération prend le même temps quelle que soit la taille du coffre. Le
     * déverrouillage rapide (qui emballe lui aussi la DEK, pas le mot de passe) reste valable.
     *
     * Le code de secours est conservé par défaut, à l'inverse de [resetMasterPassword] : ici
     * rien n'indique que la feuille papier ait été compromise, et forcer l'utilisateur à en
     * recopier une nouvelle à chaque changement est le plus sûr moyen qu'il ne le fasse pas —
     * il repartirait alors avec une feuille périmée sans le savoir. [renewRecovery] couvre le
     * cas inverse (feuille vue, photographiée, égarée) : l'ancien code est révoqué et le nouveau,
     * renvoyé, DOIT être montré. Sinon la fonction renvoie null.
     *
     * Ce que ça ne change PAS, et l'écran doit le dire : les sauvegardes `.skb` déjà exportées
     * sont chiffrées avec l'ancien mot de passe et le resteront. Renforcer un mot de passe
     * faible sans refaire de sauvegarde laisse la version faible en circulation.
     */
    fun changeMasterPassword(dek: ByteArray, newPassword: String, renewRecovery: Boolean): String? {
        val root = JSONObject(file.readText())
        wrapMaster(root, dek, newPassword)
        val recoveryCode = if (renewRecovery) wrapRecovery(root, dek) else null
        root.put("failCount", 0).put("lockedUntil", 0L)
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
