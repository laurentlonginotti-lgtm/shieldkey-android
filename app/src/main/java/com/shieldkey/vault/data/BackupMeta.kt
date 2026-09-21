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

import android.content.Context

/**
 * Fraîcheur de la sauvegarde — la seule vraie défense contre la perte du téléphone, le vol, ou
 * un rançongiciel.
 *
 * Un rançongiciel Android ne peut pas atteindre `vault.skv` : le bac à sable le lui interdit
 * (sans root). Ce qu'il atteint, c'est le stockage partagé, Drive, le PC — là où vit le `.skb`.
 * Un « anti-rançongiciel » dans le coffre n'aurait donc rien à protéger ; ce qui protège, c'est
 * une sauvegarde qui EXISTE et qui est RÉCENTE, à plusieurs endroits. Cet objet ne fait que
 * s'en assurer, et rappeler à l'utilisateur quand ce n'est plus le cas.
 *
 * Rien de secret ici (une date, un compteur, un drapeau) : SharedPreferences en clair.
 */
object BackupMeta {

    private const val PREFS = "backup_meta"
    private const val LAST_BACKUP_AT = "lastBackupAt"
    private const val CHANGES = "changesSinceBackup"
    private const val PWD_CHANGED = "passwordChangedSinceBackup"

    /** Au-delà, une sauvegarde est considérée comme périmée dès qu'il y a eu une modification. */
    const val STALE_AFTER_DAYS = 30

    private const val DAY_MS = 24L * 3600 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Une écriture du coffre (entrée, document, métadonnées) depuis la dernière sauvegarde. */
    fun noteChange(context: Context) {
        val p = prefs(context)
        p.edit().putInt(CHANGES, p.getInt(CHANGES, 0) + 1).apply()
    }

    /**
     * Le mot de passe maître a changé : les sauvegardes existantes s'ouvrent encore avec l'ANCIEN.
     * Ce n'est pas une « modification » comme une autre — c'est ce qui rend les anciennes
     * sauvegardes trompeuses, donc un rappel à part entière, quel que soit le compteur.
     */
    fun notePasswordChanged(context: Context) {
        prefs(context).edit().putBoolean(PWD_CHANGED, true).apply()
    }

    /** Une sauvegarde vient d'être écrite AVEC SUCCÈS (pas seulement produite en mémoire). */
    fun noteBackup(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(LAST_BACKUP_AT, at)
            .putInt(CHANGES, 0)
            .putBoolean(PWD_CHANGED, false)
            .apply()
    }

    /**
     * Après une restauration : le fichier restauré EST une sauvegarde de l'état courant, donc
     * rien n'est en attente. On ne connaît pas sa date d'origine ; on prend maintenant, ce qui
     * au pire retarde le prochain rappel de 30 jours.
     */
    fun noteRestored(context: Context) = noteBackup(context)

    /** Coffre neuf : rien à sauvegarder tant qu'il est vide. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    sealed class Reminder {
        object None : Reminder()
        /** Des modifications, et jamais aucune sauvegarde. */
        object Never : Reminder()
        /** Des modifications, et la dernière sauvegarde date de plus de [STALE_AFTER_DAYS] jours. */
        class Stale(val days: Int, val changes: Int) : Reminder()
        /** Le mot de passe maître a changé depuis la dernière sauvegarde. */
        object PasswordChanged : Reminder()
    }

    /**
     * Le rappel à afficher, s'il y en a un. Le changement de mot de passe prime : c'est le seul
     * cas où une sauvegarde existante est non pas vieille, mais TROMPEUSE (elle ne s'ouvre plus
     * avec le mot de passe que l'utilisateur connaît désormais).
     */
    fun reminder(context: Context, now: Long = System.currentTimeMillis()): Reminder {
        val p = prefs(context)
        if (p.getBoolean(PWD_CHANGED, false)) return Reminder.PasswordChanged
        val changes = p.getInt(CHANGES, 0)
        if (changes == 0) return Reminder.None
        val last = p.getLong(LAST_BACKUP_AT, 0L)
        if (last == 0L) return Reminder.Never
        val days = ((now - last) / DAY_MS).toInt()
        return if (days >= STALE_AFTER_DAYS) Reminder.Stale(days, changes) else Reminder.None
    }
}
