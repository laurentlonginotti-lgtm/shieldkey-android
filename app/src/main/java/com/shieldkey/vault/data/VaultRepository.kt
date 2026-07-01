package com.shieldkey.vault.data

import org.json.JSONArray
import org.json.JSONObject

/** Métadonnées d'un document rangé dans le coffre (le contenu chiffré est dans [DocumentStore]). */
data class DocumentMeta(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long
)

/**
 * Lecture / écriture des éléments du coffre (JSON déchiffré via [VaultStore]).
 * Pour l'instant : les documents (tableau "documents"). Les autres catégories
 * (connexions, cartes, crypto, notes) viendront s'ajouter ici à l'étape 4.
 */
object VaultRepository {

    private const val DOCS = "documents"

    fun listDocuments(store: VaultStore, dek: ByteArray): List<DocumentMeta> {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(DOCS) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DocumentMeta(
                id = o.getString("id"),
                name = o.optString("name", "document"),
                mime = o.optString("mime", ""),
                size = o.optLong("size", 0),
                addedAt = o.optLong("addedAt", 0)
            )
        }.sortedByDescending { it.addedAt }
    }

    fun addDocument(store: VaultStore, dek: ByteArray, meta: DocumentMeta) {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(DOCS) ?: JSONArray().also { root.put(DOCS, it) }
        arr.put(
            JSONObject()
                .put("id", meta.id)
                .put("name", meta.name)
                .put("mime", meta.mime)
                .put("size", meta.size)
                .put("addedAt", meta.addedAt)
        )
        store.writeVault(dek, root.toString())
    }

    fun removeDocument(store: VaultStore, dek: ByteArray, id: String) {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(DOCS) ?: return
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") != id) kept.put(o)
        }
        root.put(DOCS, kept)
        store.writeVault(dek, root.toString())
    }
}
