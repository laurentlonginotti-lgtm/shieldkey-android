package com.shieldkey.vault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.data.VaultStore
import com.shieldkey.vault.sound.SoundFx
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

private enum class Screen { Onboarding, RecoveryKit, Unlock, Recovery, Vault }

/**
 * Hôte de l'application : machine à états entre les écrans.
 * La DEK (clé déchiffrée) est gardée en mémoire tant que le coffre est ouvert.
 */
@Composable
fun ShieldKeyApp() {
    val context = LocalContext.current.applicationContext
    val store = remember { VaultStore(context) }

    var screen by remember { mutableStateOf(if (store.isInitialized()) Screen.Unlock else Screen.Onboarding) }
    @Suppress("UNUSED_VARIABLE")
    var dek by remember { mutableStateOf<ByteArray?>(null) }   // clé du coffre (étape 4 l'utilisera)
    var recoveryToShow by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkBgTop, SkBg, SkBgDeep)))
    ) {
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(onCreate = { pwd ->
                val res = withContext(Dispatchers.Default) { store.create(pwd) }
                dek = res.dek
                recoveryToShow = res.recoveryCode
                SoundFx.success()
                screen = Screen.RecoveryKit
            })

            Screen.RecoveryKit -> RecoveryKitScreen(code = recoveryToShow, onDone = {
                recoveryToShow = ""
                screen = Screen.Vault
            })

            Screen.Unlock -> UnlockScreen(
                onUnlock = { pwd ->
                    val k = withContext(Dispatchers.Default) { store.unlock(pwd) }
                    if (k != null) {
                        dek = k; SoundFx.success(); screen = Screen.Vault; true
                    } else {
                        SoundFx.error(); false
                    }
                },
                onForgot = { screen = Screen.Recovery }
            )

            Screen.Recovery -> RecoveryScreen(
                onRecover = { code, newPwd ->
                    val k = withContext(Dispatchers.Default) { store.unlockWithRecovery(code) }
                    if (k != null) {
                        withContext(Dispatchers.Default) { store.resetMasterPassword(k, newPwd) }
                        dek = k; SoundFx.success(); screen = Screen.Vault; true
                    } else {
                        SoundFx.error(); false
                    }
                },
                onCancel = { screen = Screen.Unlock }
            )

            Screen.Vault -> VaultScreen(onLock = {
                dek = null
                SoundFx.close()
                screen = Screen.Unlock
            })
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

@Composable
fun HintText(text: String, warn: Boolean = false) {
    Text(
        text = text,
        color = if (warn) Color(0xFFFF6B6B) else SkMuted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}
