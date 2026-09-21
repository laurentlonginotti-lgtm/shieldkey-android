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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * La primitive AES-256-GCM. Argon2id n'est pas testé ici : c'est du code natif, absent d'une
 * JVM de test — il l'est dans les tests instrumentés (émulateur), à travers VaultStore.
 */
class SkCryptoTest {

    private val key = SkCrypto.randomBytes(SkCrypto.KEY_LEN)
    private val plain = "un secret à protéger — avec des accents et des émojis 🛡️".toByteArray()

    @Test
    fun chiffrePuisDechiffreRendLeClair() {
        val blob = SkCrypto.encrypt(plain, key)
        assertArrayEquals(plain, SkCrypto.decrypt(blob, key))
    }

    @Test
    fun leBlobEstIvPlusChiffrePlusTag() {
        // iv(12) || ciphertext (même taille que le clair, pas de bourrage) || tag(16)
        val blob = SkCrypto.encrypt(plain, key)
        assertEquals(12 + plain.size + 16, blob.size)
    }

    @Test
    fun leClairVideSeChiffreAussi() {
        val blob = SkCrypto.encrypt(ByteArray(0), key)
        assertEquals(12 + 16, blob.size)
        assertArrayEquals(ByteArray(0), SkCrypto.decrypt(blob, key))
    }

    @Test
    fun deuxChiffrementsDuMemeClairDifferent() {
        // IV aléatoire à chaque appel : la réutilisation d'IV casserait GCM. Même clair, même
        // clé → blobs différents, et surtout IV différents.
        val a = SkCrypto.encrypt(plain, key)
        val b = SkCrypto.encrypt(plain, key)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
    }

    @Test
    fun centIvSontTousDistincts() {
        val ivs = (1..100).map { SkCrypto.encrypt(plain, key).copyOfRange(0, 12).toList() }.toSet()
        assertEquals(100, ivs.size)
    }

    @Test
    fun uneMauvaiseCleEstRefusee() {
        val blob = SkCrypto.encrypt(plain, key)
        val other = SkCrypto.randomBytes(SkCrypto.KEY_LEN)
        assertThrows(AEADBadTagException::class.java) { SkCrypto.decrypt(blob, other) }
    }

    @Test
    fun unOctetAltereDuChiffreEstRefuse() {
        val blob = SkCrypto.encrypt(plain, key)
        val i = 12 + plain.size / 2
        blob[i] = (blob[i].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) { SkCrypto.decrypt(blob, key) }
    }

    @Test
    fun unOctetAltereDuTagEstRefuse() {
        val blob = SkCrypto.encrypt(plain, key)
        val i = blob.size - 1
        blob[i] = (blob[i].toInt() xor 0x80).toByte()
        assertThrows(AEADBadTagException::class.java) { SkCrypto.decrypt(blob, key) }
    }

    @Test
    fun unIvAltereEstRefuse() {
        val blob = SkCrypto.encrypt(plain, key)
        blob[0] = (blob[0].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) { SkCrypto.decrypt(blob, key) }
    }

    @Test
    fun unBlobTronqueEstRefuse() {
        val blob = SkCrypto.encrypt(plain, key)
        assertThrows(Exception::class.java) { SkCrypto.decrypt(blob.copyOfRange(0, 20), key) }
        assertThrows(Exception::class.java) { SkCrypto.decrypt(ByteArray(5), key) }
    }

    @Test
    fun lesOctetsAleatoiresOntLaBonneTailleEtVarient() {
        val a = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        val b = SkCrypto.randomBytes(SkCrypto.SALT_LEN)
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun lesParametresArgon2SontCeuxAnnonces() {
        // Ces constantes sont affichées sur la page Sécurité et citées dans SECURITE.md :
        // si quelqu'un les baisse « pour la fluidité », ce test le dit.
        assertEquals(3, SkCrypto.ARGON_ITERATIONS)
        assertEquals(65536, SkCrypto.ARGON_MEMORY_KIB)
        assertEquals(2, SkCrypto.ARGON_PARALLELISM)
        assertEquals(32, SkCrypto.KEY_LEN)
    }
}
