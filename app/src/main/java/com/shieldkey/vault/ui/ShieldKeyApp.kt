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

package com.shieldkey.vault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.crypto.BiometricGate
import com.shieldkey.vault.data.BackupManager
import com.shieldkey.vault.data.VaultStore
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.util.PasswordStrength
import com.shieldkey.vault.util.SecureClipboard
import com.shieldkey.vault.ui.theme.SkBg
import com.shieldkey.vault.ui.theme.SkBgDeep
import com.shieldkey.vault.ui.theme.SkBgTop
import com.shieldkey.vault.ui.theme.SkEmerald
import com.shieldkey.vault.ui.theme.SkEmerald2
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.ui.theme.SkOnPrimary
import com.shieldkey.vault.ui.theme.SkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Screen { Onboarding, RecoveryKit, Unlock, Recovery, Vault, Restore }

/**
 * Hôte de l'application : machine à états entre les écrans.
 * La DEK (clé déchiffrée) est gardée en mémoire tant que le coffre est ouvert.
 */
@Composable
fun ShieldKeyApp() {
    val ctx = LocalContext.current
    val context = ctx.applicationContext
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val store = remember { VaultStore(context) }

    var screen by remember { mutableStateOf(if (store.isInitialized()) Screen.Unlock else Screen.Onboarding) }
    var dek by remember { mutableStateOf<ByteArray?>(null) }   // clé du coffre déchiffrée, gardée en mémoire
    var recoveryToShow by remember { mutableStateOf("") }
    var kitRenewed by remember { mutableStateOf(false) }       // le kit affiché remplace un code devenu caduc
    var showSecurity by remember { mutableStateOf(false) }     // page « Sécurité » en surimpression

    // Déverrouillage rapide (empreinte OU code de l'écran, facultatif) — voir BiometricGate.
    val bioStatus = BiometricGate.status(context)
    val bioAvailable = activity != null && bioStatus == BiometricGate.BioStatus.READY
    var bioEnabled by remember { mutableStateOf(BiometricGate.isEnabled(context)) }
    val hintNoLock = stringResource(R.string.bio_hint_no_lock)
    val bioHint = if (bioStatus == BiometricGate.BioStatus.READY) null else hintNoLock
    val bioTitle = stringResource(R.string.bio_prompt_title)
    val bioSubUnlock = stringResource(R.string.bio_prompt_sub_unlock)
    val bioSubEnable = stringResource(R.string.bio_prompt_sub_enable)

    // Verrouillage AUTOMATIQUE : dès que l'app quitte l'écran (veille/bouton power, Accueil,
    // multitâche), on efface la DEK et on repasse en écran verrouillé. Suspendu pendant une
    // demande d'auth (le prompt empreinte/code peut lui aussi mettre l'activité en pause).
    var authInProgress by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !authInProgress && dek != null) {
                dek = null
                screen = Screen.Unlock
                SecureClipboard.clearIfOurs(context)   // ne pas laisser un secret copié en veille
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkBgTop, SkBg, SkBgDeep)))
    ) {
      // Bord à bord (imposé depuis targetSdk 35) : le dégradé s'étend sous les barres système,
      // mais le contenu reste dans la zone sûre (barre d'état, barre de navigation, encoche).
      Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(
                onCreate = { pwd ->
                    val res = withContext(Dispatchers.Default) { store.create(pwd) }
                    dek = res.dek
                    recoveryToShow = res.recoveryCode
                    SoundFx.success()
                    screen = Screen.RecoveryKit
                },
                onSecurity = { showSecurity = true },
                onRestore = { screen = Screen.Restore }
            )

            Screen.RecoveryKit -> RecoveryKitScreen(
                code = recoveryToShow,
                renewed = kitRenewed,
                onDone = {
                    recoveryToShow = ""
                    kitRenewed = false
                    screen = Screen.Vault
                }
            )

            Screen.Unlock -> UnlockScreen(
                onUnlock = { pwd ->
                    val k = withContext(Dispatchers.Default) { store.unlock(pwd) }
                    if (k != null) {
                        dek = k; SoundFx.success(); screen = Screen.Vault; true
                    } else {
                        SoundFx.error(); false
                    }
                },
                onForgot = { screen = Screen.Recovery },
                onSecurity = { showSecurity = true },
                onRestore = { screen = Screen.Restore },
                biometricEnabled = bioAvailable && bioEnabled,
                onBiometric = {
                    val act = activity
                    if (act != null) {
                        authInProgress = true
                        BiometricGate.unlock(act, bioTitle, bioSubUnlock) { k ->
                            authInProgress = false
                            if (k != null) { dek = k; SoundFx.success(); screen = Screen.Vault }
                            else SoundFx.error()
                        }
                    }
                }
            )

            Screen.Recovery -> RecoveryScreen(
                onRecover = { code, newPwd ->
                    val k = withContext(Dispatchers.Default) { store.unlockWithRecovery(code) }
                    if (k != null) {
                        // Le code de secours est renouvelé en même temps que le mot de passe :
                        // on montre le nouveau kit AVANT d'entrer dans le coffre, sans quoi
                        // l'utilisateur repartirait avec une feuille qui ne vaut plus rien.
                        val newCode = withContext(Dispatchers.Default) {
                            store.resetMasterPassword(k, newPwd)
                        }
                        dek = k
                        recoveryToShow = newCode
                        kitRenewed = true
                        SoundFx.success(); screen = Screen.RecoveryKit; true
                    } else {
                        SoundFx.error(); false
                    }
                },
                onCancel = { screen = Screen.Unlock }
            )

            Screen.Restore -> RestoreScreen(
                onSuspendAutoLock = { authInProgress = it },
                onSubmit = { bytes, pwd ->
                    val restored = withContext(Dispatchers.Default) { BackupManager.import(context, bytes, pwd) }
                    var ok = false
                    if (restored) {
                        BiometricGate.disable(context)   // le déverrouillage rapide de l'ancien tél ne vaut plus
                        bioEnabled = false
                        val k = withContext(Dispatchers.Default) { store.unlock(pwd) }
                        if (k != null) { dek = k; SoundFx.success(); screen = Screen.Vault; ok = true }
                    }
                    ok
                },
                onCancel = { screen = if (store.isInitialized()) Screen.Unlock else Screen.Onboarding }
            )

            Screen.Vault -> dek?.let { currentDek ->
                VaultScreen(
                    dek = currentDek,
                    onLock = {
                        dek = null
                        SoundFx.close()
                        screen = Screen.Unlock
                        SecureClipboard.clearIfOurs(context)   // efface un éventuel secret copié
                    },
                    // Suspend le verrouillage auto pendant qu'une fenêtre système
                    // (sélecteur de fichier, visionneuse…) passe l'app en ON_STOP.
                    onSuspendAutoLock = { authInProgress = it },
                    onSecurity = { showSecurity = true },
                    biometricAvailable = bioAvailable,
                    biometricEnabled = bioEnabled,
                    biometricHint = bioHint,
                    onToggleBiometric = { turnOn ->
                        val act = activity
                        if (turnOn) {
                            if (act != null) {
                                authInProgress = true
                                BiometricGate.enable(act, currentDek, bioTitle, bioSubEnable) { ok ->
                                    authInProgress = false
                                    if (ok) { bioEnabled = true; SoundFx.success() } else SoundFx.error()
                                }
                            }
                        } else {
                            BiometricGate.disable(context); bioEnabled = false
                        }
                    }
                )
            }
        }

        // Page « Sécurité » en surimpression (accessible même verrouillé, pour la confiance).
        if (showSecurity) SecurityScreen(onClose = { showSecurity = false })
      }
    }
}

// ---------------------------------------------------------------------------
//  Composants partagés
// ---------------------------------------------------------------------------

@Composable
fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun SkLogo(size: Dp = 96.dp) {
    Image(
        painter = painterResource(id = R.drawable.ic_launcher),
        contentDescription = "ShieldKey",
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun SkPrimaryButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SkEmerald,
            contentColor = SkOnPrimary,
            disabledContainerColor = SkEmerald.copy(alpha = 0.35f),
            disabledContentColor = SkOnPrimary.copy(alpha = 0.6f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = SkOnPrimary, strokeWidth = 2.dp)
        } else {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun skFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SkEmerald,
    unfocusedBorderColor = SkMuted,
    cursorColor = SkEmerald,
    focusedLabelColor = SkEmerald2,
    unfocusedLabelColor = SkMuted,
    focusedTextColor = SkText,
    unfocusedTextColor = SkText
)

@Composable
fun SkPasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Text(
                text = if (visible) "🙈" else "👁",
                modifier = Modifier.clickable { visible = !visible }.padding(end = 12.dp),
                fontSize = 18.sp
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = skFieldColors()
    )
}

/**
 * Jauge de force du mot de passe maître. Muette tant que le champ est vide : on informe,
 * on ne réprimande pas. Le conseil « phrase de passe » ne s'affiche que tant que le mot de
 * passe n'est pas bon — une fois le niveau atteint, il disparaît.
 */
@Composable
fun PasswordStrengthMeter(pwd: String) {
    if (pwd.isEmpty()) return

    val level = PasswordStrength.level(pwd)
    val fraction = PasswordStrength.fraction(pwd)
    val color = when (level) {
        PasswordStrength.Level.WEAK -> Color(0xFFFF6B6B)
        PasswordStrength.Level.FAIR -> Color(0xFFFFB84D)
        PasswordStrength.Level.GOOD -> SkEmerald2
        PasswordStrength.Level.STRONG -> SkEmerald
    }
    val label = stringResource(
        when (level) {
            PasswordStrength.Level.WEAK -> R.string.pwd_weak
            PasswordStrength.Level.FAIR -> R.string.pwd_fair
            PasswordStrength.Level.GOOD -> R.string.pwd_good
            PasswordStrength.Level.STRONG -> R.string.pwd_strong
        }
    )

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = color, fontSize = 11.sp)
        if (level == PasswordStrength.Level.WEAK || level == PasswordStrength.Level.FAIR) {
            Text(stringResource(R.string.pwd_tip), color = SkMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun HintText(text: String, warn: Boolean = false) {
    Text(
        text = text,
        color = if (warn) Color(0xFFFF6B6B) else SkMuted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}

/** Remonte la chaîne des Context pour retrouver la FragmentActivity (requise par BiometricPrompt). */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
