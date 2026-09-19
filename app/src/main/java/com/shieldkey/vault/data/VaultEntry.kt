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
