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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var msg by remember { mutableStateOf<String?>(null) }

    val tooShort = pwd.isNotEmpty() && pwd.length < 8
    val mismatch = confirm.isNotEmpty() && confirm != pwd
    val canSubmit = pwd.length >= 8 && pwd == confirm && !loading

    CenteredColumn {
        SkLogo()
        Spacer(Modifier.height(14.dp))
        Text("Créez votre coffre", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Choisissez un mot de passe maître. C'est la seule clé de votre coffre : choisissez-le fort et mémorisable.",
            color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        SkPasswordField(pwd, { pwd = it; msg = null }, "Mot de passe maître")
        if (tooShort) HintText("Au moins 8 caractères", warn = true)
        Spacer(Modifier.height(10.dp))
        SkPasswordField(confirm, { confirm = it; msg = null }, "Confirmer le mot de passe")
        if (mismatch) HintText("Les mots de passe ne correspondent pas", warn = true)
        msg?.let { HintText(it, warn = true) }
        Spacer(Modifier.height(24.dp))
        SkPrimaryButton("Créer le coffre", enabled = canSubmit, loading = loading) {
            scope.launch {
                loading = true
                try {
                    onCreate(pwd)
                } catch (e: Exception) {
                    msg = "Erreur : ${e.message}"
                    SoundFx.error()
                }
                loading = false
            }
        }
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
        Text("🔑 Votre kit de secours", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ce code est la SEULE façon de récupérer votre coffre si vous oubliez votre mot de passe maître. Notez-le ou imprimez-le, et gardez-le en lieu sûr.",
            color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
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
            Text("📋 Copier le code", color = SkEmerald2)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1AFF6B6B))
                .padding(12.dp)
        ) {
            Text(
                "⚠️ Sans ce code ET sans votre mot de passe, vos données seront définitivement perdues. Personne ne peut les récupérer.",
                color = Color(0xFFFFB4A2), fontSize = 12.sp
            )
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
            Text("J'ai noté mon code de secours en lieu sûr", color = SkText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        SkPrimaryButton("Entrer dans mon coffre", enabled = saved) { onDone() }
    }
}

// ---------------------------------------------------------------------------
//  Déverrouillage
// ---------------------------------------------------------------------------
@Composable
fun UnlockScreen(onUnlock: suspend (String) -> Boolean, onForgot: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pwd by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    CenteredColumn {
        SkLogo()
        Spacer(Modifier.height(14.dp))
        Text("ShieldKey", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Votre coffre est verrouillé", color = SkMuted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        SkPasswordField(pwd, { pwd = it; error = false }, "Mot de passe maître")
        if (error) HintText("Mot de passe incorrect", warn = true)
        Spacer(Modifier.height(20.dp))
        SkPrimaryButton("Déverrouiller", enabled = pwd.isNotEmpty(), loading = loading) {
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
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onForgot) {
            Text("Mot de passe oublié ? Utiliser le code de secours", color = SkMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("🔒 100 % hors-ligne", color = SkMuted, fontSize = 11.sp)
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
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = code.isNotBlank() && pwd.length >= 8 && pwd == confirm && !loading

    CenteredColumn {
        Text("Récupération", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Entrez votre code de secours, puis choisissez un nouveau mot de passe maître.",
            color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; error = null },
            label = { Text("Code de secours") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = skFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        SkPasswordField(pwd, { pwd = it; error = null }, "Nouveau mot de passe maître")
        Spacer(Modifier.height(10.dp))
        SkPasswordField(confirm, { confirm = it; error = null }, "Confirmer")
        error?.let { HintText(it, warn = true) }
        Spacer(Modifier.height(20.dp))
        SkPrimaryButton("Récupérer mon coffre", enabled = canSubmit, loading = loading) {
            scope.launch {
                loading = true
                val ok = onRecover(code, pwd)
                if (!ok) error = "Code de secours invalide"
                loading = false
            }
        }
        TextButton(onClick = onCancel) { Text("Annuler", color = SkMuted) }
    }
}

// ---------------------------------------------------------------------------
//  Coffre (vide pour l'instant — l'ajout d'entrées arrive à l'étape 4)
// ---------------------------------------------------------------------------
@Composable
fun VaultScreen(onLock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkLogo(36.dp)
            Spacer(Modifier.width(10.dp))
            Text("ShieldKey", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLock) { Text("🔒 Verrouiller", color = SkEmerald2, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(12.dp)
        ) {
            Text("🔒 100 % hors-ligne · Chiffré AES-256 · Argon2id", color = SkMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Tout", true)
            CategoryChip("🔑", false)
            CategoryChip("💳", false)
            CategoryChip("₿", false)
            CategoryChip("📝", false)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗄️", fontSize = 46.sp)
            Spacer(Modifier.height(8.dp))
            Text("Votre coffre est vide", color = SkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ajoutez votre première entrée avec le bouton ci-dessous.",
                color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        SkPrimaryButton("+  Ajouter une entrée", enabled = true) { /* étape 4 */ }
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
