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

package com.shieldkey.vault.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicWriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun ecritUnNouveauFichier() {
        val f = File(tmp.root, "vault.skv")
        AtomicWrite.write(f, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), f.readBytes())
    }

    @Test
    fun neLaissePasDeFichierTemporaire() {
        val f = File(tmp.root, "vault.skv")
        AtomicWrite.write(f, byteArrayOf(1))
        assertFalse(File(tmp.root, "vault.skv.tmp").exists())
        assertArrayEquals(arrayOf("vault.skv"), tmp.root.list()!!.sortedArray())
    }

    @Test
    fun remplaceEntierementUnFichierExistant() {
        // Un contenu plus court que l'ancien ne doit laisser aucun octet résiduel : c'est
        // le rename qui remplace, pas une écriture en place.
        val f = File(tmp.root, "vault.skv")
        AtomicWrite.write(f, ByteArray(1000) { 7 })
        AtomicWrite.write(f, byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(9, 9), f.readBytes())
    }

    @Test
    fun ecritUnContenuVide() {
        val f = File(tmp.root, "empty")
        AtomicWrite.write(f, ByteArray(0))
        assertTrue(f.exists())
        assertArrayEquals(ByteArray(0), f.readBytes())
    }

    @Test
    fun echoueProprementSiLeDossierNexistePas() {
        val f = File(File(tmp.root, "absent"), "vault.skv")
        assertThrows(Exception::class.java) { AtomicWrite.write(f, byteArrayOf(1)) }
        assertFalse(f.exists())
        assertFalse(File(f.path + ".tmp").exists())
    }

    @Test
    fun unTmpResiduelEstEcraseParLEcritureSuivante() {
        // Interruption simulée entre l'écriture du .tmp et le rename : l'écriture suivante
        // doit passer par-dessus sans s'en soucier.
        val f = File(tmp.root, "vault.skv")
        File(tmp.root, "vault.skv.tmp").writeBytes(byteArrayOf(0, 0, 0))
        AtomicWrite.write(f, byteArrayOf(4, 2))
        assertArrayEquals(byteArrayOf(4, 2), f.readBytes())
        assertFalse(File(tmp.root, "vault.skv.tmp").exists())
    }
}
