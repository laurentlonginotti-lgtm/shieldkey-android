package com.shieldkey.vault.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Déverrouillage biométrique (empreinte / visage) SANS affaiblir le zero-knowledge.
 *
 * Principe (chiffrement à enveloppe, comme la DEK) :
 *  - Une clé AES-256 est générée DANS l'enclave matérielle (Android Keystore / StrongBox),
 *    configurée pour n'être utilisable qu'après une authentification biométrique FORTE
 *    (setUserAuthenticationRequired). Cette clé ne quitte JAMAIS le matériel.
 *  - Cette clé emballe la DEK du coffre ; seul le blob chiffré (iv || ciphertext) est écrit
 *    sur le disque interne privé (bio.skv). Sans le capteur + le matériel de CE téléphone,
 *    le blob est inutile.
 *
 * Sécurité :
 *  - Si l'utilisateur enrôle une NOUVELLE empreinte, la clé est invalidée
 *    (setInvalidatedByBiometricEnrollment) → réactivation obligatoire au mot de passe maître.
 *  - Le mot de passe maître reste le rempart ultime : la biométrie n'est qu'un raccourci local.
 */
object BiometricGate {

    private const val KEY_ALIAS = "shieldkey_bio_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    private fun blobFile(context: Context) = File(context.filesDir, "bio.skv")

    /** Le matériel biométrique est présent ET au moins un doigt / visage est enrôlé. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** L'utilisateur a activé le déverrouillage biométrique (le blob emballé existe). */
    fun isEnabled(context: Context): Boolean = blobFile(context).exists()

    /** Désactive : efface le blob emballé + la clé matérielle. */
    fun disable(context: Context) {
        blobFile(context).delete()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) { /* rien à nettoyer */ }
    }

    // --- Clé matérielle ------------------------------------------------------

    private fun existingKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) { null }

    private fun createKey(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(builder.build()); generateKey()
            }
        } catch (e: Exception) {
            // StrongBox indisponible sur cet appareil → repli sur le TEE logiciel.
            if (strongBox) createKey(strongBox = false) else throw e
        }
    }

    // --- Activation ----------------------------------------------------------

    /**
     * Active la biométrie : demande l'empreinte, puis emballe [dek] avec la clé matérielle.
     * [onResult] = true si activé, false si annulé / échec.
     */
    fun enable(
        activity: FragmentActivity,
        dek: ByteArray,
        title: String,
        subtitle: String,
        cancel: String,
        onResult: (Boolean) -> Unit
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = try {
            (existingKey() ?: createKey(strongBox = true)).also {
                cipher.init(Cipher.ENCRYPT_MODE, it)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Clé périmée (nouvelle empreinte enrôlée) → on repart d'une clé fraîche.
            disable(activity)
            try {
                createKey(strongBox = true).also { cipher.init(Cipher.ENCRYPT_MODE, it) }
            } catch (ex: Exception) { onResult(false); return }
        } catch (e: Exception) {
            onResult(false); return
        }
        if (key == null) { onResult(false); return }

        prompt(activity, title, subtitle, cancel, cipher) { authed ->
            if (authed == null) { onResult(false); return@prompt }
            try {
                val ct = authed.doFinal(dek)
                blobFile(activity).writeBytes(authed.iv + ct)
                onResult(true)
            } catch (e: Exception) { onResult(false) }
        }
    }

    // --- Déverrouillage ------------------------------------------------------

    /**
     * Déverrouille via biométrie : demande l'empreinte, puis renvoie la DEK déchiffrée.
     * [onResult] = la DEK, ou null si annulé / échec.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancel: String,
        onResult: (ByteArray?) -> Unit
    ) {
        val blob = try { blobFile(activity).readBytes() } catch (e: Exception) { onResult(null); return }
        if (blob.size <= IV_LEN) { onResult(null); return }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)

        val key = existingKey()
        if (key == null) { onResult(null); return }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Empreinte changée depuis l'activation : on invalide, retour au mot de passe.
            disable(activity); onResult(null); return
        } catch (e: Exception) {
            onResult(null); return
        }

        prompt(activity, title, subtitle, cancel, cipher) { authed ->
            if (authed == null) { onResult(null); return@prompt }
            try {
                onResult(authed.doFinal(ct))
            } catch (e: Exception) { onResult(null) }
        }
    }

    // --- Prompt système commun ----------------------------------------------

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancel: String,
        cipher: Cipher,
        onCipher: (Cipher?) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCipher(null)
            override fun onAuthenticationFailed() { /* mauvaise empreinte : le système laisse réessayer */ }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onCipher(result.cryptoObject?.cipher)
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
