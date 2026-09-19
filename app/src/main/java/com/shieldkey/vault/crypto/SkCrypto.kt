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

package com.shieldkey.vault.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitives cryptographiques de ShieldKey.
 *  - Dérivation de clé : Argon2id (résistant GPU/ASIC)
 *  - Chiffrement : AES-256-GCM (authentifié : la déchiffre échoue si la clé est fausse
 *    OU si les données ont été altérées)
 */
object SkCrypto {

    private val rng = SecureRandom()
    private val argon2 by lazy { Argon2Kt() }

    // Paramètres Argon2id (compromis sécurité / fluidité sur mobile)
    const val ARGON_ITERATIONS = 3
    const val ARGON_MEMORY_KIB = 65536   // 64 Mo
    const val ARGON_PARALLELISM = 2

    const val KEY_LEN = 32               // AES-256
    const val SALT_LEN = 16
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    /** Dérive une clé de 32 octets à partir d'un secret (mot de passe / code de secours). */
    fun deriveKey(secret: ByteArray, salt: ByteArray): ByteArray {
        val res = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = secret,
            salt = salt,
            tCostInIterations = ARGON_ITERATIONS,
            mCostInKibibyte = ARGON_MEMORY_KIB,
            parallelism = ARGON_PARALLELISM,
            hashLengthInBytes = KEY_LEN
        )
        return res.rawHashAsByteArray()
    }

    /** Chiffre. Renvoie iv(12) || ciphertext||tag. */
    fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        val iv = randomBytes(GCM_IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    /** Déchiffre. Lève une exception (AEADBadTagException) si la clé est fausse ou les données altérées. */
    fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, GCM_IV_LEN)
        val ct = blob.copyOfRange(GCM_IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
