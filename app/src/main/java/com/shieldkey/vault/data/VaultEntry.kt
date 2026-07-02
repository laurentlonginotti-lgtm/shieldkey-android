package com.shieldkey.vault.data

/** Catégories d'entrées « classiques » du coffre (les documents sont gérés à part). */
enum class EntryType(val id: String, val icon: String) {
    LOGIN("login", "🔑"),
    CARD("card", "💳"),
    CRYPTO("crypto", "₿"),
    NOTE("note", "📝");

    companion object {
        fun from(id: String?): EntryType = values().firstOrNull { it.id == id } ?: LOGIN
    }
}

/**
 * Une entrée du coffre : un titre + une carte de champs (clé → valeur) selon son [type].
 * Le contenu vit chiffré dans le coffre JSON (voir [VaultRepository]) ; les valeurs vides
 * ne sont pas enregistrées.
 */
data class VaultEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val fields: Map<String, String>,
    val updatedAt: Long
)
