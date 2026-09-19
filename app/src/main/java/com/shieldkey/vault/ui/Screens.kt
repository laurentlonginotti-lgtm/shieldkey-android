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
import android.content.Intent
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.style.TextOverflow
import com.shieldkey.vault.data.BackupManager
import com.shieldkey.vault.data.DocumentMeta
import com.shieldkey.vault.data.DocumentStore
import com.shieldkey.vault.data.EntryType
import com.shieldkey.vault.data.VaultEntry
import com.shieldkey.vault.data.VaultRepository
import com.shieldkey.vault.data.VaultStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
//  Création du coffre
// ---------------------------------------------------------------------------
@Composable
fun OnboardingScreen(
    onCreate: suspend (String) -> Unit,
    onSecurity: () -> Unit = {},
    onRestore: () -> Unit = {}
) {
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
        TextButton(onClick = onSecurity) { Text(stringResource(R.string.sec_open), color = SkEmerald2, fontSize = 13.sp) }
        TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_open), color = SkEmerald2, fontSize = 13.sp) }
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
    onSecurity: () -> Unit = {},
    onRestore: () -> Unit = {},
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
        TextButton(onClick = onSecurity) { Text(stringResource(R.string.sec_open), color = SkEmerald2, fontSize = 13.sp) }
        TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_open), color = SkEmerald2, fontSize = 13.sp) }
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
    dek: ByteArray,
    onLock: () -> Unit,
    onSuspendAutoLock: (Boolean) -> Unit = {},
    onSecurity: () -> Unit = {},
    biometricAvailable: Boolean = false,
    biometricEnabled: Boolean = false,
    biometricHint: String? = null,
    onToggleBiometric: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { VaultStore(context) }
    val docStore = remember { DocumentStore(context) }

    var entries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var docs by remember { mutableStateOf<List<DocumentMeta>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf<CatFilter>(CatFilter.All) }
    var sub by remember { mutableStateOf<VaultSub>(VaultSub.List) }
    var showBackupPrompt by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingBackupPwd by remember { mutableStateOf("") }
    val wrongPwd = stringResource(R.string.unlock_wrong)

    LaunchedEffect(reload) {
        val (e, d) = withContext(Dispatchers.IO) {
            VaultRepository.listEntries(store, dek) to VaultRepository.listDocuments(store, dek)
        }
        entries = e
        docs = d
    }

    // Sélecteur de fichier système (SAF) : aucune permission de stockage requise.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onSuspendAutoLock(false)   // de retour du sélecteur : le verrouillage auto reprend
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val cr = context.contentResolver
                        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
                        var name = "document"
                        var size = bytes.size.toLong()
                        cr.query(uri, null, null, null, null)?.use { c ->
                            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val si = c.getColumnIndex(OpenableColumns.SIZE)
                            if (c.moveToFirst()) {
                                if (ni >= 0) c.getString(ni)?.let { name = it }
                                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                            }
                        }
                        val mime = cr.getType(uri) ?: "application/octet-stream"
                        val id = docStore.save(dek, bytes)
                        VaultRepository.addDocument(
                            store, dek, DocumentMeta(id, name, mime, size, System.currentTimeMillis())
                        )
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                busy = false
                if (ok) { reload++; SoundFx.success() } else SoundFx.error()
            }
        }
    }

    // --- Ouverture / visualisation d'un document (hors-ligne) ---
    // On déchiffre vers un fichier TEMPORAIRE en clair (cacheDir/open/), partagé à la
    // visionneuse via FileProvider, puis on l'efface au retour et à la fermeture du coffre.
    val openTempDir = remember { File(context.cacheDir, "open") }
    fun clearOpenTemp() {
        try { openTempDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
    }
    DisposableEffect(Unit) {
        clearOpenTemp()                 // repart propre (reliquat d'un arrêt brutal éventuel)
        onDispose { clearOpenTemp() }   // on quitte/verrouille le coffre → efface le clair
    }
    // Lancée via un launcher pour être notifié du RETOUR (réactiver le verrouillage + nettoyer).
    val viewer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onSuspendAutoLock(false)
        clearOpenTemp()
    }
    fun openDocument(meta: DocumentMeta) {
        scope.launch {
            busy = true
            val intent = withContext(Dispatchers.IO) {
                try {
                    clearOpenTemp()
                    openTempDir.mkdirs()
                    val bytes = docStore.read(dek, meta.id)
                    val safeName = meta.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document" }
                    val f = File(openTempDir, safeName)
                    f.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, meta.mime.ifBlank { "application/octet-stream" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: Exception) { null }
            }
            busy = false
            if (intent == null) { SoundFx.error(); return@launch }
            onSuspendAutoLock(true)
            try {
                viewer.launch(intent)
            } catch (e: Exception) {    // aucune appli capable d'ouvrir ce type de fichier
                onSuspendAutoLock(false)
                clearOpenTemp()
                SoundFx.error()
            }
        }
    }

    // --- Sauvegarde du coffre vers un fichier .skb chiffré (SAF, hors du téléphone) ---
    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        onSuspendAutoLock(false)
        val pwd = pendingBackupPwd
        pendingBackupPwd = ""
        if (uri != null && pwd.isNotEmpty()) {
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val bytes = BackupManager.export(context, pwd)
                        val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                        out.use { it.write(bytes) }
                        true
                    } catch (e: Exception) { false }
                }
                busy = false
                if (ok) SoundFx.success() else SoundFx.error()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    when (val s = sub) {
        VaultSub.ChooseType -> ChooseTypeScreen(
            onPick = { t -> sub = VaultSub.Edit(t, null) },
            onDocument = {
                sub = VaultSub.List
                onSuspendAutoLock(true)   // sélecteur système : ne pas verrouiller
                picker.launch(arrayOf("*/*"))
            },
            onCancel = { sub = VaultSub.List }
        )

        is VaultSub.Edit -> EntryEditorScreen(
            type = s.type,
            existing = s.existing,
            onCancel = { sub = s.existing?.let { VaultSub.View(it) } ?: VaultSub.List },
            onSave = { entry ->
                scope.launch {
                    withContext(Dispatchers.IO) { VaultRepository.upsertEntry(store, dek, entry) }
                    reload++
                    SoundFx.success()
                    sub = VaultSub.View(entry)
                }
            }
        )

        is VaultSub.View -> EntryDetailScreen(
            entry = s.entry,
            onEdit = { sub = VaultSub.Edit(s.entry.type, s.entry) },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { VaultRepository.removeEntry(store, dek, s.entry.id) }
                    reload++
                    SoundFx.close()
                    sub = VaultSub.List
                }
            },
            onClose = { sub = VaultSub.List }
        )

        VaultSub.List -> {
            val c = cat
            val filteredEntries = when (c) {
                CatFilter.All -> entries
                is CatFilter.Type -> entries.filter { it.type == c.type }
                CatFilter.Docs -> emptyList()
            }
            val showDocs = c == CatFilter.All || c == CatFilter.Docs
            val isEmpty = filteredEntries.isEmpty() && (!showDocs || docs.isEmpty())

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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                        .clickable { onSecurity() }.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.vault_secure_badge), color = SkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("›", color = SkEmerald2, fontSize = 16.sp)
                    }
                }
                // Réglage déverrouillage rapide : visible seulement dans la vue « Tout » (déclutter).
                if (c == CatFilter.All) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF))
                            .then(if (biometricAvailable) Modifier.clickable { onToggleBiometric(!biometricEnabled) } else Modifier)
                            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = if (biometricHint != null) 10.dp else 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (biometricEnabled) R.string.bio_enabled else R.string.bio_enable),
                                color = if (biometricAvailable) SkText else SkMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = biometricEnabled,
                                enabled = biometricAvailable,
                                onCheckedChange = { onToggleBiometric(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SkOnPrimary,
                                    checkedTrackColor = SkEmerald
                                )
                            )
                        }
                        if (biometricHint != null) {
                            Text(
                                biometricHint,
                                color = SkMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                if (c == CatFilter.All) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                            .clickable { backupError = null; showBackupPrompt = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.backup_open), color = SkText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("›", color = SkEmerald2, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChip(stringResource(R.string.chip_all), c == CatFilter.All) { cat = CatFilter.All }
                    CategoryChip("🔑", c is CatFilter.Type && c.type == EntryType.LOGIN) { cat = CatFilter.Type(EntryType.LOGIN) }
                    CategoryChip("💳", c is CatFilter.Type && c.type == EntryType.CARD) { cat = CatFilter.Type(EntryType.CARD) }
                    CategoryChip("₿", c is CatFilter.Type && c.type == EntryType.CRYPTO) { cat = CatFilter.Type(EntryType.CRYPTO) }
                    CategoryChip("📝", c is CatFilter.Type && c.type == EntryType.NOTE) { cat = CatFilter.Type(EntryType.NOTE) }
                    CategoryChip("📄", c == CatFilter.Docs) { cat = CatFilter.Docs }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                ) {
                    if (isEmpty) {
                        Spacer(Modifier.height(48.dp))
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (c == CatFilter.Docs) {
                                Text("📄", fontSize = 46.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.doc_empty_title), color = SkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.doc_empty_sub), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                            } else {
                                Text("🛡️", fontSize = 46.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.vault_empty_title), color = SkText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.vault_empty_sub), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        filteredEntries.forEach { entry ->
                            EntryRow(entry = entry, onClick = { sub = VaultSub.View(entry) })
                            Spacer(Modifier.height(8.dp))
                        }
                        if (showDocs) {
                            docs.forEach { meta ->
                                DocumentRow(
                                    meta = meta,
                                    onOpen = { openDocument(meta) },
                                    onDelete = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                docStore.delete(meta.id)
                                                VaultRepository.removeDocument(store, dek, meta.id)
                                            }
                                            reload++
                                            SoundFx.close()
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SkPrimaryButton(
                    stringResource(if (c == CatFilter.Docs) R.string.doc_add else R.string.vault_add),
                    enabled = !busy,
                    loading = busy
                ) {
                    when (val cc = cat) {
                        CatFilter.Docs -> {
                            onSuspendAutoLock(true)   // sélecteur système : ne pas verrouiller
                            picker.launch(arrayOf("*/*"))
                        }
                        is CatFilter.Type -> sub = VaultSub.Edit(cc.type, null)
                        CatFilter.All -> sub = VaultSub.ChooseType
                    }
                }
            }
        }
    }

        if (showBackupPrompt) {
            PasswordDialog(
                title = stringResource(R.string.backup_title),
                message = stringResource(R.string.backup_prompt),
                confirmLabel = stringResource(R.string.backup_action),
                error = backupError,
                loading = busy,
                onConfirm = { pwd ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { store.unlock(pwd) != null }
                        if (ok) {
                            backupError = null
                            showBackupPrompt = false
                            pendingBackupPwd = pwd
                            onSuspendAutoLock(true)
                            backupSaver.launch(
                                "ShieldKey-sauvegarde-" +
                                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".skb"
                            )
                        } else backupError = wrongPwd
                    }
                },
                onCancel = { showBackupPrompt = false; backupError = null }
            )
        }
    }
}

@Composable
private fun DocumentRow(meta: DocumentMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable { onOpen() }   // toucher la ligne = ouvrir le document
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📄", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meta.name,
                color = SkText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(Formatter.formatShortFileSize(context, meta.size), color = SkMuted, fontSize = 11.sp)
        }
        Text(
            "🗑",
            fontSize = 18.sp,
            modifier = Modifier.clickable { onDelete() }.padding(6.dp)
        )
    }
}

@Composable
fun CategoryChip(label: String, on: Boolean, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) SkEmerald else Color(0x14FFFFFF))
            .clickable { onClick() }
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

// ---------------------------------------------------------------------------
//  Boîte de dialogue « mot de passe » (surimpression, réutilisable)
// ---------------------------------------------------------------------------
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    error: String?,
    loading: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC05090F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF10192B))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = SkMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            SkPasswordField(pwd, { pwd = it }, stringResource(R.string.field_master_password))
            if (error != null) HintText(error, warn = true)
            Spacer(Modifier.height(16.dp))
            SkPrimaryButton(confirmLabel, enabled = pwd.isNotBlank(), loading = loading) { onConfirm(pwd) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
        }
    }
}

// ---------------------------------------------------------------------------
//  Restauration d'une sauvegarde (nouveau téléphone)
// ---------------------------------------------------------------------------
@Composable
fun RestoreScreen(
    onSuspendAutoLock: (Boolean) -> Unit,
    onSubmit: suspend (ByteArray, String) -> Boolean,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pwd by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onSuspendAutoLock(false)
        if (uri != null) {
            scope.launch {
                fileBytes = withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
                }
                error = false
            }
        }
    }

    CenteredColumn {
        SkLogo()
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.restore_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.restore_warning), color = SkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x14FFFFFF))
                .clickable { onSuspendAutoLock(true); picker.launch(arrayOf("*/*")) }
                .padding(16.dp)
        ) {
            Text(
                if (fileBytes != null) stringResource(R.string.restore_file_ok) else stringResource(R.string.restore_pick),
                color = if (fileBytes != null) SkEmerald2 else SkText,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.restore_prompt), color = SkMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SkPasswordField(pwd, { pwd = it; error = false }, stringResource(R.string.field_master_password))
        if (error) HintText(stringResource(R.string.restore_error), warn = true)
        Spacer(Modifier.height(20.dp))
        SkPrimaryButton(
            stringResource(R.string.restore_action),
            enabled = fileBytes != null && pwd.isNotBlank(),
            loading = loading
        ) {
            val bytes = fileBytes ?: return@SkPrimaryButton
            scope.launch {
                loading = true
                val ok = onSubmit(bytes, pwd)
                if (!ok) { error = true; SoundFx.error() }
                loading = false
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
    }
}
