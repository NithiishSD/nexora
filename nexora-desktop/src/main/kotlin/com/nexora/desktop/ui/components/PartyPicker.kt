package com.nexora.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexora.desktop.repository.PartyRepository
import org.koin.compose.koinInject

/**
 * Picks an active party holding [role] (Customer, Supplier, ...) by name, code or phone
 * (`GET /parties?search=&role=&status=ACTIVE`, 06 section 6).
 */
@Composable
fun PartyPicker(
    label: String,
    role: String?,
    selected: PickOption?,
    onPick: (PickOption) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val parties: PartyRepository = koinInject()
    EntityPicker(label, selected, { text -> parties.pick(text, role) }, onPick, modifier, error, enabled, tag)
}
