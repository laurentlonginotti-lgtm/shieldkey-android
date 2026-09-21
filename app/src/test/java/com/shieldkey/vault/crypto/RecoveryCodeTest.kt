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
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {

    private val format = Regex("^[0-9A-Z]{5}(-[0-9A-Z]{5}){6}$")

    @Test
    fun leFormatEstSeptGroupesDeCinq() {
        repeat(50) {
            val code = RecoveryCode.generate()
            assertTrue("format inattendu : $code", format.matches(code))
        }
    }

    @Test
    fun lesLettresAmbiguesSontExclues() {
        // I, L, O, U n'existent pas dans l'alphabet : impossible de les confondre avec 1 et 0
        // sur une feuille manuscrite. Sur 200 codes (7 000 symboles), aucune ne doit apparaître.
        repeat(200) {
            val code = RecoveryCode.generate()
            assertFalse("lettre ambiguë dans $code", code.any { it in "ILOU" })
        }
    }

    @Test
    fun deuxCentsCodesSontTousDistincts() {
        val codes = (1..200).map { RecoveryCode.generate() }.toSet()
        assertEquals(200, codes.size)
    }

    @Test
    fun laNormalisationRetireTiretsEtEspaces() {
        val a = RecoveryCode.normalize("7H2KQ-9MX4T-ABCDE")
        val b = RecoveryCode.normalize("7H2KQ 9MX4T ABCDE")
        val c = RecoveryCode.normalize("7H2KQ9MX4TABCDE")
        assertArrayEquals(a, b)
        assertArrayEquals(a, c)
        assertEquals(15, a.size)
    }

    @Test
    fun laNormalisationIgnoreLaCasse() {
        assertArrayEquals(RecoveryCode.normalize("7H2KQ-9MX4T"), RecoveryCode.normalize("7h2kq-9mx4t"))
    }

    @Test
    fun laNormalisationCorrigeLesConfusionsVisuelles() {
        // Quelqu'un qui recopie « 1 » en « l » ou « I », « 0 » en « O », doit quand même rouvrir.
        assertArrayEquals(RecoveryCode.normalize("10101"), RecoveryCode.normalize("lOIOl"))
        assertArrayEquals(RecoveryCode.normalize("10101"), RecoveryCode.normalize("IoIoI"))
    }

    @Test
    fun unCodeGenereSeNormaliseEnTrenteCinqSymboles() {
        val code = RecoveryCode.generate()
        val n = RecoveryCode.normalize(code)
        assertEquals(35, n.size)
        assertArrayEquals(code.replace("-", "").toByteArray(Charsets.US_ASCII), n)
    }
}
