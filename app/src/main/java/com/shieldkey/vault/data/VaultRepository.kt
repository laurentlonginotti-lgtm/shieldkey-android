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
    private const val ENTRIES = "entries"

    // --- Entrées (connexions, cartes/IBAN, crypto, notes) ---

    fun listEntries(store: VaultStore, dek: ByteArray): List<VaultEntry> {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(ENTRIES) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val fo = o.optJSONObject("fields") ?: JSONObject()
            val fields = LinkedHashMap<String, String>()
            for (k in fo.keys()) fields[k] = fo.optString(k, "")
            VaultEntry(
                id = o.getString("id"),
                type = EntryType.from(o.optString("type")),
                title = o.optString("title", ""),
                fields = fields,
                updatedAt = o.optLong("updatedAt", 0)
            )
        }.sortedByDescending { it.updatedAt }
    }

    /** Ajoute l'entrée, ou remplace celle qui a le même id (édition). */
    fun upsertEntry(store: VaultStore, dek: ByteArray, entry: VaultEntry) {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(ENTRIES) ?: JSONArray().also { root.put(ENTRIES, it) }
        val fo = JSONObject()
        entry.fields.forEach { (k, v) -> fo.put(k, v) }
        val obj = JSONObject()
            .put("id", entry.id)
            .put("type", entry.type.id)
            .put("title", entry.title)
            .put("updatedAt", entry.updatedAt)
            .put("fields", fo)
        var replaced = false
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("id") == entry.id) {
                arr.put(i, obj); replaced = true; break
            }
        }
        if (!replaced) arr.put(obj)
        store.writeVault(dek, root.toString())
    }

    fun removeEntry(store: VaultStore, dek: ByteArray, id: String) {
        val root = JSONObject(store.readVault(dek))
        val arr = root.optJSONArray(ENTRIES) ?: return
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") != id) kept.put(o)
        }
        root.put(ENTRIES, kept)
        store.writeVault(dek, root.toString())
    }

    // --- Documents ---

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
