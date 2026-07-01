package com.shieldkey.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.ui.theme.SkEmerald
import com.shieldkey.vault.ui.theme.SkEmerald2
import com.shieldkey.vault.ui.theme.SkGold
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.ui.theme.SkOnPrimary
import com.shieldkey.vault.ui.theme.SkText
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
//  Création du coffre
// ---------------------------------------------------------------------------
@Composable
fun OnboardingScreen(onCreate: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val tooShort = pwd.isNotEmpty() && pwd.length < 8
    val mismatch = confirm.isNotEmpty() && confirm != pwd
    val canSubmit = pwd.length >= 8 && pwd == confirm && !loading

    CenteredColumn {
        SkLogo()
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.onb_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onb_subtitle), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        SkPasswordField(pwd, { pwd = it; errMsg = null }, stringResource(R.string.field_master_password))
        if (tooShort) HintText(stringResource(R.string.err_min8), warn = true)
        Spacer(Modifier.height(10.dp))
        SkPasswordField(confirm, { confirm = it; errMsg = null }, stringResource(R.string.field_confirm_password))
        if (mismatch) HintText(stringResource(R.string.err_mismatch), warn = true)
        errMsg?.let { HintText(stringResource(R.string.err_generic, it), warn = true) }
        Spacer(Modifier.height(24.dp))
        SkPrimaryButton(stringResource(R.string.onb_create), enabled = canSubmit, loading = loading) {
            scope.launch {
                loading = true
                try {
                    onCreate(pwd)
                } catch (e: Exception) {
                    errMsg = e.message ?: "?"
                    SoundFx.error()
                }
                loading = false
            }
        }
        Spacer(Modifier.height(10.dp))
        LanguageButton()
    }
}

// ---------------------------------------------------------------------------
//  Kit de secours
// ---------------------------------------------------------------------------
@Composable
fun RecoveryKitScreen(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }

    CenteredColumn {
        Text(stringResource(R.string.kit_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.kit_subtitle), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, SkGold, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Text(
                code,
                color = SkGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
            Text(stringResource(R.string.kit_copy), color = SkEmerald2)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1AFF6B6B))
                .padding(12.dp)
        ) {
            Text(stringResource(R.string.kit_warning), color = Color(0xFFFFB4A2), fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { saved = !saved }
        ) {
            Checkbox(
                checked = saved,
                onCheckedChange = { saved = it },
                colors = CheckboxDefaults.colors(checkedColor = SkEmerald)
            )
            Text(stringResource(R.string.kit_checkbox), color = SkText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        SkPrimaryButton(stringResource(R.string.kit_enter), enabled = saved) { onDone() }
    }
}

// ---------------------------------------------------------------------------
//  Déverrouillage
// ---------------------------------------------------------------------------
@Composable
fun UnlockScreen(
    onUnlock: suspend (String) -> Boolean,
    onForgot: () -> Unit,
    biometricEnabled: Boolean = false,
    onBiometric: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var pwd by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    // Si la biométrie est active, on propose l'empreinte dès l'ouverture de l'écran.
    LaunchedEffect(Unit) { if (biometricEnabled) onBiometric() }

    CenteredColumn {
        SkLogo()
        Spacer(Modifier.height(14.dp))
        Text("ShieldKey", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.unlock_locked), color = SkMuted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        SkPasswordField(pwd, { pwd = it; error = false }, stringResource(R.string.field_master_password))
        if (error) HintText(stringResource(R.string.unlock_wrong), warn = true)
        Spacer(Modifier.height(20.dp))
        SkPrimaryButton(stringResource(R.string.unlock_button), enabled = pwd.isNotEmpty(), loading = loading) {
            scope.launch {
                loading = true
                val ok = onUnlock(pwd)
                if (!ok) {
                    error = true
                    pwd = ""
                }
                loading = false
            }
        }
        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBiometric) {
                Text(stringResource(R.string.bio_unlock_button), color = SkEmerald2, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onForgot) {
            Text(stringResource(R.string.unlock_forgot), color = SkMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.offline_badge), color = SkMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        LanguageButton()
    }
}

// ---------------------------------------------------------------------------
//  Récupération via code de secours
// ---------------------------------------------------------------------------
@Composable
fun RecoveryScreen(onRecover: suspend (String, String) -> Boolean, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    val canSubmit = code.isNotBlank() && pwd.length >= 8 && pwd == confirm && !loading

    CenteredColumn {
        Text(stringResource(R.string.rec_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.rec_subtitle), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; invalid = false },
            label = { Text(stringResource(R.string.field_recovery_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = skFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        SkPasswordField(pwd, { pwd = it }, stringResource(R.string.field_new_master))
        Spacer(Modifier.height(10.dp))
        SkPasswordField(confirm, { confirm = it }, stringResource(R.string.field_confirm))
        if (invalid) HintText(stringResource(R.string.rec_invalid), warn = true)
        Spacer(Modifier.height(20.dp))
        SkPrimaryButton(stringResource(R.string.rec_button), enabled = canSubmit, loading = loading) {
            scope.launch {
                loading = true
                val ok = onRecover(code, pwd)
                if (!ok) invalid = true
                loading = false
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
    }
}

// ---------------------------------------------------------------------------
//  Coffre (vide pour l'instant — l'ajout d'entrées arrive à l'étape 4)
// ---------------------------------------------------------------------------
@Composable
fun VaultScreen(
    onLock: () -> Unit,
    biometricAvailable: Boolean = false,
    biometricEnabled: Boolean = false,
    onToggleBiometric: (Boolean) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkLogo(36.dp)
            Spacer(Modifier.width(10.dp))
            Text("ShieldKey", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLock) { Text(stringResource(R.string.vault_lock), color = SkEmerald2, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(12.dp)
        ) {
            Text(stringResource(R.string.vault_secure_badge), color = SkMuted, fontSize = 12.sp)
        }
        if (biometricAvailable) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onToggleBiometric(!biometricEnabled) }
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(if (biometricEnabled) R.string.bio_enabled else R.string.bio_enable),
                    color = SkText, fontSize = 13.sp, modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { onToggleBiometric(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SkOnPrimary,
                        checkedTrackColor = SkEmerald
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(stringResource(R.string.chip_all), true)
            CategoryChip("🔑", false)
            CategoryChip("💳", false)
            CategoryChip("₿", false)
            CategoryChip("📝", false)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗄️", fontSize = 46.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.vault_empty_title), color = SkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.vault_empty_sub), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        SkPrimaryButton(stringResource(R.string.vault_add), enabled = true) { /* étape 4 */ }
    }
}

@Composable
fun CategoryChip(label: String, on: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) SkEmerald else Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (on) SkOnPrimary else SkMuted,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}
