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

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * Détection d'un accès root — pour AVERTIR, jamais pour bloquer.
 *
 * Pourquoi avertir : tout le modèle de sécurité de ShieldKey hors ouverture repose sur le bac à
 * sable d'Android (personne ne lit `filesDir`, personne ne lit la mémoire d'une autre appli).
 * Le root abolit ce cloisonnement : une appli à qui l'utilisateur accorde le root peut lire le
 * fichier, la DEK pendant que le coffre est ouvert, ou ce qui est tapé. Le chiffrement, lui,
 * tient toujours — coffre fermé, le fichier reste illisible sans le mot de passe.
 *
 * Pourquoi ne jamais bloquer : (1) cette détection se contourne (Magisk « DenyList » masque
 * `su` et son paquet à une appli donnée) — un attaquant outillé passe, seul l'utilisateur
 * honnête serait puni ; (2) un faux positif enfermerait quelqu'un HORS de ses propres données ;
 * (3) le root est un choix légitime de l'utilisateur, souvent le même public que celui qui
 * cherche un coffre hors-ligne. On informe, on laisse juger.
 *
 * Ce qu'on ne fait PAS, volontairement : exécuter `su` (déclencherait une demande de droits
 * root sur le téléphone de l'utilisateur), utiliser l'API Play Integrity (Google + réseau :
 * contraire au 100 % hors-ligne), ou regarder `Build.TAGS` (les ROM alternatives signent en
 * `test-keys` sans être rootées — trop de faux positifs pour un simple avertissement).
 */
object RootCheck {

    /** Emplacements classiques du binaire `su`. Un `stat` par chemin, rien n'est exécuté. */
    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/system/sbin/su", "/sbin/su", "/su/bin/su",
        "/vendor/bin/su", "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
        "/system/sd/xbin/su", "/system/bin/failsafe/su", "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su", "/system/app/Superuser.apk", "/system/app/SuperSU.apk"
    )

    /** Gestionnaires de root connus. Doivent aussi figurer dans <queries> du manifeste (Android 11+). */
    private val ROOT_MANAGERS = listOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "com.kingroot.kinguser",
        "me.phh.superuser"
    )

    /** Un indice de root a été trouvé. « Semble » : ni preuve, ni garantie de l'inverse. */
    fun isLikelyRooted(context: Context): Boolean =
        hasSuBinary() || hasRootManager(context)

    private fun hasSuBinary(): Boolean {
        val onPath = System.getenv("PATH")
            ?.split(':')
            ?.filter { it.isNotBlank() }
            ?.map { "$it/su" }
            .orEmpty()
        return (SU_PATHS + onPath).any { path ->
            try { File(path).exists() } catch (e: Exception) { false }
        }
    }

    private fun hasRootManager(context: Context): Boolean {
        val pm = context.packageManager
        return ROOT_MANAGERS.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                false
            }
        }
    }
}
