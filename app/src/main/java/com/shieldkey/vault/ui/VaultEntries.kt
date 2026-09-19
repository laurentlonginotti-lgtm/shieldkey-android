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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldkey.vault.R
import com.shieldkey.vault.data.EntryType
import com.shieldkey.vault.data.VaultEntry
import com.shieldkey.vault.sound.SoundFx
import com.shieldkey.vault.util.SecureClipboard
import com.shieldkey.vault.ui.theme.SkEmerald2
import com.shieldkey.vault.ui.theme.SkMuted
import com.shieldkey.vault.ui.theme.SkText
import java.util.UUID

// ---------------------------------------------------------------------------
//  Navigation interne du coffre + filtre de catégorie
// ---------------------------------------------------------------------------
sealed interface CatFilter {
    object All : CatFilter
    data class Type(val type: EntryType) : CatFilter
    object Docs : CatFilter
}

sealed interface VaultSub {
    object List : VaultSub
    object ChooseType : VaultSub
    data class Edit(val type: EntryType, val existing: VaultEntry?) : VaultSub
    data class View(val entry: VaultEntry) : VaultSub
}

// ---------------------------------------------------------------------------
//  Schéma des champs par type d'entrée
// ---------------------------------------------------------------------------
data class FieldSpec(
    val key: String,
    val labelRes: Int,
    val sensitive: Boolean = false,
    val multiline: Boolean = false,
    val keyboard: KeyboardType = KeyboardType.Text
)

fun fieldsFor(type: EntryType): List<FieldSpec> = when (type) {
    EntryType.LOGIN -> listOf(
        FieldSpec("username", R.string.field_username),
        FieldSpec("password", R.string.field_password, sensitive = true, keyboard = KeyboardType.Password),
        FieldSpec("url", R.string.field_url, keyboard = KeyboardType.Uri),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    EntryType.CARD -> cardFields(CardKind.PHYSICAL)   // défaut ; le vrai genre est choisi à l'édition
    EntryType.CRYPTO -> listOf(
        FieldSpec("seed", R.string.field_seed, sensitive = true, multiline = true),
        FieldSpec("privateKey", R.string.field_private_key, sensitive = true, multiline = true),
        FieldSpec("wallet", R.string.field_wallet),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    EntryType.NOTE -> listOf(
        FieldSpec("body", R.string.field_note_body, multiline = true)
    )
}

/**
 * Genre d'une entrée 💳 — le champ réservé "kind" du coffre le stocke, et il fait varier les
 * champs affichés. Une carte physique, une e-carte jetable, un enrôlement Click to Pay et un
 * IBAN/RIB ne portent pas les mêmes informations.
 */
enum class CardKind(val id: String, val labelRes: Int) {
    PHYSICAL("physical", R.string.card_kind_physical),
    ECARD("ecard", R.string.card_kind_ecard),
    CLICK_TO_PAY("clicktopay", R.string.card_kind_ctp),
    MOBILE("mobile", R.string.card_kind_mobile),
    IBAN("iban", R.string.card_kind_iban);

    companion object {
        fun from(id: String?): CardKind = values().firstOrNull { it.id == id } ?: PHYSICAL
    }
}

fun cardFields(kind: CardKind): List<FieldSpec> = when (kind) {
    CardKind.PHYSICAL -> listOf(
        FieldSpec("holder", R.string.field_holder),
        FieldSpec("number", R.string.field_card_number, sensitive = true, keyboard = KeyboardType.Number),
        FieldSpec("expiry", R.string.field_expiry, keyboard = KeyboardType.Number),
        FieldSpec("cvv", R.string.field_cvv, sensitive = true, keyboard = KeyboardType.Number),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    CardKind.ECARD -> listOf(
        FieldSpec("holder", R.string.field_holder),
        FieldSpec("number", R.string.field_card_number, sensitive = true, keyboard = KeyboardType.Number),
        FieldSpec("expiry", R.string.field_expiry, keyboard = KeyboardType.Number),
        FieldSpec("cvv", R.string.field_cvv, sensitive = true, keyboard = KeyboardType.Number),
        FieldSpec("limit", R.string.field_card_limit, keyboard = KeyboardType.Number),
        FieldSpec("validity", R.string.field_card_validity),
        FieldSpec("source", R.string.field_card_source),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    CardKind.CLICK_TO_PAY -> listOf(
        FieldSpec("email", R.string.field_ctp_email, keyboard = KeyboardType.Email),
        FieldSpec("cards", R.string.field_ctp_cards, multiline = true),
        FieldSpec("network", R.string.field_ctp_network),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    CardKind.MOBILE -> listOf(
        FieldSpec("wallet", R.string.field_wallet_app),
        FieldSpec("cards", R.string.field_ctp_cards, multiline = true),
        FieldSpec("account", R.string.field_wallet_account),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
    CardKind.IBAN -> listOf(
        FieldSpec("holder", R.string.field_holder),
        FieldSpec("iban", R.string.field_iban, sensitive = true),
        FieldSpec("bic", R.string.field_bic),
        FieldSpec("bank", R.string.field_bank),
        FieldSpec("notes", R.string.field_notes, multiline = true)
    )
}

fun typeLabelRes(type: EntryType): Int = when (type) {
    EntryType.LOGIN -> R.string.entry_type_login
    EntryType.CARD -> R.string.entry_type_card
    EntryType.CRYPTO -> R.string.entry_type_crypto
    EntryType.NOTE -> R.string.entry_type_note
}

private fun entrySubtitle(e: VaultEntry): String = when (e.type) {
    EntryType.LOGIN -> e.fields["username"].orEmpty()
    EntryType.CARD -> when (CardKind.from(e.fields["kind"])) {
        CardKind.CLICK_TO_PAY -> e.fields["email"].orEmpty()
        CardKind.MOBILE -> e.fields["wallet"].orEmpty()
        CardKind.IBAN -> maskCard(e.fields["iban"].orEmpty())
        else -> maskCard(e.fields["number"].orEmpty())
    }
    EntryType.CRYPTO -> e.fields["wallet"].orEmpty()
    EntryType.NOTE -> e.fields["body"].orEmpty().lineSequence().firstOrNull()?.take(48).orEmpty()
}

private fun maskCard(num: String): String {
    val digits = num.filter { it.isDigit() }
    return if (digits.length >= 4) "•••• " + digits.takeLast(4) else num
}

// ---------------------------------------------------------------------------
//  Ligne d'une entrée dans la liste
// ---------------------------------------------------------------------------
@Composable
fun EntryRow(entry: VaultEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.type.icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.ifBlank { stringResource(typeLabelRes(entry.type)) },
                color = SkText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = entrySubtitle(entry)
            if (sub.isNotBlank()) {
                Text(sub, color = SkMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = SkMuted, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------------------
//  Choix du type à ajouter
// ---------------------------------------------------------------------------
@Composable
fun ChooseTypeScreen(
    onPick: (EntryType) -> Unit,
    onDocument: () -> Unit,
    onCancel: () -> Unit
) {
    CenteredColumn {
        Text(
            stringResource(R.string.choose_type_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        EntryType.values().forEach { t ->
            TypeCard(t.icon, stringResource(typeLabelRes(t))) { onPick(t) }
            Spacer(Modifier.height(10.dp))
        }
        TypeCard("📄", stringResource(R.string.choose_type_document)) { onDocument() }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
    }
}

@Composable
private fun TypeCard(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = SkText, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("›", color = SkMuted, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------------------
//  Formulaire d'ajout / édition
// ---------------------------------------------------------------------------
@Composable
fun EntryEditorScreen(
    type: EntryType,
    existing: VaultEntry?,
    onCancel: () -> Unit,
    onSave: (VaultEntry) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    // Pour une carte, le genre (physique / e-carte / Click to Pay / IBAN) fait varier les champs.
    var cardKind by remember { mutableStateOf(CardKind.from(existing?.fields?.get("kind"))) }
    val specs = if (type == EntryType.CARD) cardFields(cardKind) else fieldsFor(type)
    val values = remember {
        mutableStateMapOf<String, String>().apply { existing?.fields?.forEach { (k, v) -> put(k, v) } }
    }
    val canSave = title.isNotBlank()

    CenteredColumn {
        Text("${type.icon}  ${stringResource(typeLabelRes(type))}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        if (type == EntryType.CARD) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardKind.values().forEach { k ->
                    CategoryChip(stringResource(k.labelRes), cardKind == k) { cardKind = k }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = skFieldColors()
        )
        Spacer(Modifier.height(12.dp))

        specs.forEach { spec ->
            val v = values[spec.key] ?: ""
            when {
                spec.sensitive -> SkSecretField(v, { values[spec.key] = it }, stringResource(spec.labelRes), spec.multiline, spec.keyboard)
                else -> OutlinedTextField(
                    value = v,
                    onValueChange = { values[spec.key] = it },
                    label = { Text(stringResource(spec.labelRes)) },
                    singleLine = !spec.multiline,
                    minLines = if (spec.multiline) 3 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = spec.keyboard),
                    modifier = Modifier.fillMaxWidth(),
                    colors = skFieldColors()
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!canSave) HintText(stringResource(R.string.err_title_required), warn = true)
        Spacer(Modifier.height(10.dp))
        SkPrimaryButton(stringResource(R.string.action_save), enabled = canSave) {
            val fields = LinkedHashMap<String, String>()
            if (type == EntryType.CARD) fields["kind"] = cardKind.id   // champ réservé (genre de carte)
            specs.forEach { s -> values[s.key]?.trim()?.let { if (it.isNotEmpty()) fields[s.key] = it } }
            onSave(
                VaultEntry(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    type = type,
                    title = title.trim(),
                    fields = fields,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = SkMuted) }
    }
}

/**
 * Champ sensible (masqué par défaut, œil pour révéler, gère le multiligne pour les seeds).
 *
 * Le clavier reste TOUJOURS en variante « mot de passe » (texte ou numérique), même quand le
 * champ est révélé : c'est cette variante qui dit au clavier de ne rien apprendre ni suggérer.
 * Sinon, une seed de 24 mots tapée en clair (ce qu'on fait forcément pour se relire) serait
 * mémorisée par Gboard et proposée en suggestion… dans n'importe quelle autre appli.
 * Révéler ne change que l'affichage, jamais le type de clavier.
 */
@Composable
fun SkSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    multiline: Boolean,
    keyboard: KeyboardType
) {
    var visible by remember { mutableStateOf(false) }
    val secretKeyboard = when (keyboard) {
        KeyboardType.Number, KeyboardType.NumberPassword -> KeyboardType.NumberPassword
        else -> KeyboardType.Password
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = secretKeyboard, autoCorrectEnabled = false),
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

// ---------------------------------------------------------------------------
//  Détail d'une entrée (révéler / copier / modifier / supprimer)
// ---------------------------------------------------------------------------
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        CenteredColumn {
            Text(entry.type.icon, fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                entry.title.ifBlank { stringResource(typeLabelRes(entry.type)) },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            val cardKind = if (entry.type == EntryType.CARD) CardKind.from(entry.fields["kind"]) else null
            val subtitle = stringResource(typeLabelRes(entry.type)) +
                (cardKind?.let { " · " + stringResource(it.labelRes) } ?: "")
            Text(subtitle, color = SkMuted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            val specs = if (cardKind != null) cardFields(cardKind) else fieldsFor(entry.type)
            var shown = 0
            specs.forEach { spec ->
                val v = entry.fields[spec.key].orEmpty()
                if (v.isNotBlank()) {
                    shown++
                    FieldView(
                        label = stringResource(spec.labelRes),
                        value = v,
                        sensitive = spec.sensitive
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (shown == 0) {
                Text(stringResource(R.string.vault_empty_sub), color = SkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            SkPrimaryButton(stringResource(R.string.action_edit)) { onEdit() }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.action_delete), color = Color(0xFFFF6B6B))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back), color = SkMuted) }
        }

        if (confirmDelete) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC05090F)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .padding(28.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF10192B))
                        .border(1.dp, Color(0x33FF6B6B), RoundedCornerShape(18.dp))
                        .padding(22.dp)
                ) {
                    Text(stringResource(R.string.entry_delete_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.entry_delete_msg), color = SkMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_cancel), color = SkMuted)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { confirmDelete = false; onDelete() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldView(label: String, value: String, sensitive: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(!sensitive) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SkEmerald2, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (sensitive) {
                Text(
                    if (revealed) "🙈" else "👁",
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { revealed = !revealed }.padding(horizontal = 6.dp)
                )
            }
            Text(
                "📋",
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable {
                        // Champ sensible : presse-papier durci (marqué sensible + auto-effacé).
                        if (sensitive) SecureClipboard.copySensitive(context, value)
                        else clipboard.setText(AnnotatedString(value))
                        SoundFx.inject()
                    }
                    .padding(start = 6.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (revealed) value else "•".repeat(value.length.coerceIn(6, 20)),
            color = SkText,
            fontSize = 15.sp
        )
        if (sensitive && !revealed) {
            Text(stringResource(R.string.reveal_hidden), color = SkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
