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

package com.shieldkey.vault.i18n

/**
 * Une langue proposée par ShieldKey.
 *  - [tag] : étiquette BCP 47 (« de », « pt-BR »…) — c'est ce que [LocaleHelper] enregistre
 *    et applique. Doit correspondre à un dossier `res/values-<qualifieur>/` ET à une entrée de
 *    `res/xml/locales_config.xml` (Android 13+ : « Langues par appli »).
 *  - [nativeName] : nom de la langue DANS cette langue (convention des sélecteurs : un
 *    utilisateur qui ne lit pas l'interface actuelle doit reconnaître la sienne).
 */
data class AppLanguage(val tag: String, val nativeName: String)

/**
 * Table unique des langues livrées. Pour en ajouter une : une ligne ici + `values-xx/strings.xml`
 * + une ligne dans `locales_config.xml`. Rien d'autre à toucher.
 *
 * L'anglais est la langue par défaut (`res/values/`).
 */
object Languages {
    val all: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("es", "Español"),
        AppLanguage("it", "Italiano"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("pl", "Polski"),
    )

    /** Nom natif d'une langue enregistrée, ou null si inconnue / mode « Système ». */
    fun nativeName(tag: String): String? = all.firstOrNull { it.tag == tag }?.nativeName
}
