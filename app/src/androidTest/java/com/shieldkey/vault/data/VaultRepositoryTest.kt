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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldkey.vault.data.TestFiles.PWD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Les entrées et les métadonnées de documents, à travers le coffre chiffré. */
@RunWith(AndroidJUnit4::class)
class VaultRepositoryTest {

    private val ctx get() = TestFiles.context
    private lateinit var store: VaultStore
    private lateinit var dek: ByteArray

    @Before
    fun setUp() {
        TestFiles.wipe()
        store = VaultStore(ctx)
        dek = store.create(PWD).dek
    }

    @After
    fun tearDown() = TestFiles.wipe()

    private fun entry(id: String, title: String, at: Long, type: EntryType = EntryType.LOGIN) =
        VaultEntry(id, type, title, mapOf("password" to "p-$id", "notes" to "n-$id"), at)

    @Test
    fun unCoffreNeufEstVide() {
        assertTrue(VaultRepository.listEntries(store, dek).isEmpty())
        assertTrue(VaultRepository.listDocuments(store, dek).isEmpty())
    }

    @Test
    fun ajouteEtRelitUneEntreeAvecSesChamps() {
        VaultRepository.upsertEntry(store, dek, entry("a", "Banque", 10L, EntryType.CARD))
        val list = VaultRepository.listEntries(store, dek)
        assertEquals(1, list.size)
        assertEquals("a", list[0].id)
        assertEquals(EntryType.CARD, list[0].type)
        assertEquals("Banque", list[0].title)
        assertEquals("p-a", list[0].fields["password"])
        assertEquals("n-a", list[0].fields["notes"])
        assertEquals(10L, list[0].updatedAt)
    }

    @Test
    fun laListeEstTrieeDuPlusRecentAuPlusAncien() {
        VaultRepository.upsertEntry(store, dek, entry("vieux", "Vieux", 1L))
        VaultRepository.upsertEntry(store, dek, entry("recent", "Récent", 3L))
        VaultRepository.upsertEntry(store, dek, entry("milieu", "Milieu", 2L))
        assertEquals(listOf("recent", "milieu", "vieux"), VaultRepository.listEntries(store, dek).map { it.id })
    }

    @Test
    fun modifierRemplaceSansDupliquer() {
        VaultRepository.upsertEntry(store, dek, entry("a", "Avant", 1L))
        VaultRepository.upsertEntry(store, dek, entry("a", "Après", 2L))
        val list = VaultRepository.listEntries(store, dek)
        assertEquals(1, list.size)
        assertEquals("Après", list[0].title)
    }

    @Test
    fun supprimeUneEntree() {
        VaultRepository.upsertEntry(store, dek, entry("a", "A", 1L))
        VaultRepository.upsertEntry(store, dek, entry("b", "B", 2L))
        VaultRepository.removeEntry(store, dek, "a")
        assertEquals(listOf("b"), VaultRepository.listEntries(store, dek).map { it.id })
        // Supprimer un id inconnu ne casse rien.
        VaultRepository.removeEntry(store, dek, "zzz")
        assertEquals(1, VaultRepository.listEntries(store, dek).size)
    }

    @Test
    fun unTypeInconnuRetombeSurConnexion() {
        // Un coffre venu d'une version future avec un type que cette version ignore doit rester lisible.
        assertEquals(EntryType.LOGIN, EntryType.from("hologramme"))
        assertEquals(EntryType.LOGIN, EntryType.from(null))
        assertEquals(EntryType.CRYPTO, EntryType.from("crypto"))
    }

    @Test
    fun ajouteEtSupprimeUnDocument() {
        val meta = DocumentMeta("d1", "carte-identite.pdf", "application/pdf", 1234L, 5L)
        VaultRepository.addDocument(store, dek, meta)
        val list = VaultRepository.listDocuments(store, dek)
        assertEquals(1, list.size)
        assertEquals(meta, list[0])
        VaultRepository.removeDocument(store, dek, "d1")
        assertTrue(VaultRepository.listDocuments(store, dek).isEmpty())
    }

    @Test
    fun entreesEtDocumentsCohabitent() {
        VaultRepository.upsertEntry(store, dek, entry("a", "A", 1L))
        VaultRepository.addDocument(store, dek, DocumentMeta("d1", "x.pdf", "application/pdf", 1L, 1L))
        assertEquals(1, VaultRepository.listEntries(store, dek).size)
        assertEquals(1, VaultRepository.listDocuments(store, dek).size)
        VaultRepository.removeEntry(store, dek, "a")
        assertEquals(1, VaultRepository.listDocuments(store, dek).size)
    }

    @Test
    fun chaqueEcritureCompteEnAttenteDeSauvegarde() {
        assertTrue(BackupMeta.reminder(ctx) is BackupMeta.Reminder.None)
        VaultRepository.upsertEntry(store, dek, entry("a", "A", 1L))
        assertTrue(BackupMeta.reminder(ctx) is BackupMeta.Reminder.Never)
    }

    @Test
    fun lesDocumentsSontChiffresUnParUn() {
        val docs = DocumentStore(ctx)
        val id1 = docs.save(dek, "premier".toByteArray())
        val id2 = docs.save(dek, "second".toByteArray())
        assertFalse(id1 == id2)
        assertEquals("premier", String(docs.read(dek, id1)))
        assertEquals("second", String(docs.read(dek, id2)))
        docs.delete(id1)
        assertEquals("second", String(docs.read(dek, id2)))
    }
}
