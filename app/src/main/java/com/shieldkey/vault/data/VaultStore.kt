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
import java.security.MessageDigest

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

    private val context = context.applicationContext
    private val file = File(context.filesDir, "vault.skv")

    fun isInitialized(): Boolean = file.exists()

    /**
     * Le fichier existe mais n'est plus lisible : JSON invalide, champ manquant, base64 altéré.
     * Ce n'est PAS un mauvais mot de passe, et il faut le dire à l'utilisateur — sinon il tape
     * dix fois le bon, se fait freiner, et ne pense jamais à restaurer sa sauvegarde.
     */
    class CorruptedVaultException : RuntimeException("vault.skv illisible")

    private val requiredFields = listOf("masterSalt", "masterWrap", "recoverySalt", "recoveryWrap", "vault")

    /** Tailles fixes : un sel fait SALT_LEN octets, un emballage de DEK fait iv(12) || 32 || tag(16). */
    private val expectedLen = mapOf(
        "masterSalt" to SkCrypto.SALT_LEN,
        "recoverySalt" to SkCrypto.SALT_LEN,
        "masterWrap" to 12 + SkCrypto.KEY_LEN + 16,
        "recoveryWrap" to 12 + SkCrypto.KEY_LEN + 16
    )

    /**
     * Empreinte SHA-256 des cinq champs chiffrés, posée à chaque écriture, vérifiée à chaque lecture.
     *
     * Ce n'est PAS une défense contre un attaquant — il la recalculerait ; l'intégrité face à lui,
     * c'est GCM qui la garantit, sous la clé. C'est un détecteur de DÉGÂTS : support défaillant,
     * copie tronquée, éditeur de texte trop zélé. Sans elle, un sel abîmé ne lève rien — le décodeur
     * Base64 d'Android saute les caractères invalides en silence — et dérive simplement une autre
     * clé : l'utilisateur taperait dix fois le bon mot de passe en lisant « incorrect ».
     */
    private fun checksum(root: JSONObject): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (k in requiredFields) {
            md.update(root.optString(k, "").toByteArray(Charsets.UTF_8))
            md.update(0)   // séparateur : "ab"+"c" ≠ "a"+"bc"
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Lit le fichier et vérifie sa structure. Lève [CorruptedVaultException] si elle est altérée. */
    private fun readRoot(): JSONObject {
        val root = try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            throw CorruptedVaultException()
        }
        if (!requiredFields.all { root.has(it) }) throw CorruptedVaultException()
        // Un fichier d'avant l'empreinte n'en a pas : il en recevra une à sa prochaine écriture.
        val stored = root.optString("checksum", "")
        if (stored.isNotEmpty() && stored != checksum(root)) throw CorruptedVaultException()
        for ((k, len) in expectedLen) {
            if (bytes(root, k).size != len) throw CorruptedVaultException()
        }
        return root
    }

    /** Un champ binaire du fichier ; un base64 altéré est une corruption, pas une erreur banale. */
    private fun bytes(root: JSONObject, key: String): ByteArray = try {
        unb64(root.getString(key))
    } catch (e: Exception) {
        throw CorruptedVaultException()
    }

    /** Le coffre est présent mais illisible (sans avoir besoin du mot de passe pour le savoir). */
    fun isCorrupted(): Boolean = file.exists() && try {
        val root = readRoot()
        requiredFields.forEach { bytes(root, it) }
        false
    } catch (e: CorruptedVaultException) {
        true
    }

    /** Le contenu chiffré du coffre se déchiffre bien avec cette DEK (sinon : blob altéré). */
    fun vaultDecrypts(dek: ByteArray): Boolean = try {
        SkCrypto.decrypt(bytes(readRoot(), "vault"), dek)
        true
    } catch (e: Exception) {
        false
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    /** Seul point d'écriture du coffre : atomique + durable, empreinte d'intégrité comprise. */
    private fun save(root: JSONObject) {
        root.put("checksum", checksum(root))
        AtomicWrite.write(file, root.toString().toByteArray(Charsets.UTF_8))
    }

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
        BackupMeta.reset(context)   // coffre vide : rien à sauvegarder encore
        return CreateResult(dek, recoveryCode)
    }

    /** Issue d'une tentative de déverrouillage. */
    sealed class UnlockResult {
        class Success(val dek: ByteArray) : UnlockResult()
        object WrongPassword : UnlockResult()
        /** Trop d'essais : la saisie est refusée pendant [secondsLeft] secondes. */
        class Throttled(val secondsLeft: Long) : UnlockResult()
        /** Le fichier est altéré : aucun mot de passe ne l'ouvrira, il faut restaurer une sauvegarde. */
        object Corrupted : UnlockResult()
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

    /**
     * Déverrouille avec le mot de passe maître.
     *
     * Deux diagnostics de corruption, tous deux certains : un fichier dont la structure ne se lit
     * pas (avant même de dériver quoi que ce soit), et un `masterWrap` qui s'ouvre — donc le mot
     * de passe est bon — mais un `vault` qui ne se déchiffre pas. Dans les deux cas, insister
     * sur le mot de passe ne mènera nulle part : seule une sauvegarde peut réparer.
     */
    fun unlock(masterPassword: String): UnlockResult = try {
        unlockOrThrow(masterPassword)
    } catch (e: CorruptedVaultException) {
        UnlockResult.Corrupted
    }

    private fun unlockOrThrow(masterPassword: String): UnlockResult {
        val root = readRoot()

        val lockedUntil = root.optLong("lockedUntil", 0L)
        val now = System.currentTimeMillis()
        if (lockedUntil > now) {
            return UnlockResult.Throttled((lockedUntil - now + 999) / 1000)
        }

        val masterKey = SkCrypto.deriveKey(
            masterPassword.toByteArray(Charsets.UTF_8),
            bytes(root, "masterSalt")
        )
        val dek = try {
            SkCrypto.decrypt(bytes(root, "masterWrap"), masterKey)
        } catch (e: CorruptedVaultException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            // La clé dérivée ne sert qu'ici : on ne la laisse pas traîner dans le tas.
            masterKey.fill(0)
        }

        return if (dek != null) {
            // Bon mot de passe mais contenu illisible : c'est le fichier qui est atteint.
            try {
                SkCrypto.decrypt(bytes(root, "vault"), dek)
            } catch (e: Exception) {
                throw CorruptedVaultException()
            }
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

    /**
     * Récupération : déverrouille via le code de secours. [UnlockResult.WrongPassword] signifie
     * ici « code de secours invalide » ; pas de freinage sur ce chemin (175 bits, indevinable).
     */
    fun unlockWithRecovery(recoveryCode: String): UnlockResult = try {
        val root = readRoot()
        val recoveryKey = SkCrypto.deriveKey(
            RecoveryCode.normalize(recoveryCode),
            bytes(root, "recoverySalt")
        )
        val dek = try {
            SkCrypto.decrypt(bytes(root, "recoveryWrap"), recoveryKey)
        } catch (e: CorruptedVaultException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            recoveryKey.fill(0)
        }
        when {
            dek == null -> UnlockResult.WrongPassword
            !vaultDecrypts(dek) -> UnlockResult.Corrupted
            else -> UnlockResult.Success(dek)
        }
    } catch (e: CorruptedVaultException) {
        UnlockResult.Corrupted
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
        val root = readRoot()
        wrapMaster(root, dek, newPassword)
        val recoveryCode = wrapRecovery(root, dek)
        // Un mot de passe redéfini remet aussi le compteur d'essais à zéro.
        root.put("failCount", 0).put("lockedUntil", 0L)
        save(root)
        BackupMeta.notePasswordChanged(context)
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
        val root = readRoot()
        wrapMaster(root, dek, newPassword)
        val recoveryCode = if (renewRecovery) wrapRecovery(root, dek) else null
        root.put("failCount", 0).put("lockedUntil", 0L)
        save(root)
        BackupMeta.notePasswordChanged(context)
        return recoveryCode
    }

    /** Lit le contenu déchiffré du coffre (JSON) avec la DEK. */
    fun readVault(dek: ByteArray): String {
        val root = readRoot()
        return String(SkCrypto.decrypt(bytes(root, "vault"), dek), Charsets.UTF_8)
    }

    /** Écrit (chiffre) le contenu du coffre avec la DEK. Chaque écriture éloigne de la dernière sauvegarde. */
    fun writeVault(dek: ByteArray, vaultJson: String) {
        val root = readRoot()
        root.put("vault", b64(SkCrypto.encrypt(vaultJson.toByteArray(Charsets.UTF_8), dek)))
        save(root)
        BackupMeta.noteChange(context)
    }
}
