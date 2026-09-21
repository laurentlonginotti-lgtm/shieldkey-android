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

package com.shieldkey.vault

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shieldkey.vault.crypto.BiometricGate
import com.shieldkey.vault.data.TestFiles
import com.shieldkey.vault.util.RootCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ce qui dépend du téléphone lui-même (capteur, root, permissions) : on ne peut pas prédire
 * la valeur sur un émulateur, on vérifie que rien ne plante et que le manifeste dit vrai.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private val ctx get() = TestFiles.context

    @Test
    fun laDetectionDeRootNePlantePas() {
        // Un émulateur est souvent « rooté » (image userdebug) : la valeur n'est pas testable,
        // seule l'absence d'exception l'est.
        RootCheck.isLikelyRooted(ctx)
    }

    @Test
    fun leDeverrouillageRapideEstEteintSurUnTelephoneVierge() {
        TestFiles.wipe()
        assertFalse(BiometricGate.isEnabled(ctx))
        BiometricGate.status(ctx)   // ne doit pas lever
    }

    @Test
    fun aucunePermissionReseauNestDemandee() {
        // Le cœur de la promesse « 0 Internet » : vérifié sur le vrai manifeste installé.
        val pm = ctx.packageManager
        val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        assertFalse("INTERNET demandé !", requested.contains("android.permission.INTERNET"))
        assertFalse(requested.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(requested.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(requested.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(requested.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
    }

    @Test
    fun seulesLesPermissionsAnnonceesSontDemandees() {
        // Le manifeste FUSIONNÉ, dépendances comprises : androidx.biometric ajoute USE_FINGERPRINT
        // (l'ancien nom de la même permission locale), androidx.core une permission privée à
        // l'appli pour ses receivers. Rien d'autre ne doit apparaître — une bibliothèque qui
        // glisserait INTERNET ou une permission de stockage serait attrapée ici.
        val pm = ctx.packageManager
        val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet() ?: emptySet()
        assertEquals(
            setOf(
                "android.permission.USE_BIOMETRIC",
                "android.permission.USE_FINGERPRINT",
                "android.permission.HIDE_OVERLAY_WINDOWS",
                ctx.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            ),
            requested
        )
    }

    @Test
    fun laSauvegardeSystemeEstDesactivee() {
        val flags = ctx.applicationInfo.flags
        assertTrue((flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) == 0)
    }

    @Test
    fun instrumentationCibleLaBonneAppli() {
        assertEquals("com.shieldkey.vault.debug", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }
}
