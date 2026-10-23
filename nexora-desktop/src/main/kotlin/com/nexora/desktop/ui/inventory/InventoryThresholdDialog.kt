package com.nexora.desktop.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FlagChip
import com.nexora.desktop.ui.components.ModalBox
import com.nexora.desktop.ui.components.SubmitButton
import com.nexora.desktop.ui.components.WeightField
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.inventory.InventoryViewModel

@Composable
fun InventoryThresholdDialog(onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: InventoryViewModel = screenViewModel()
    val state by vm.state.collectAsState()

    ModalBox(onDismiss = onCancel, width = 640.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s("inventory.thresholdsTitle"), style = MaterialTheme.typography.titleLarge)
            Text(
                s("inventory.thresholdsHelp"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.error?.let { ErrorBanner(it) }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s("purchase.product"), Modifier.weight(2.2f), fontWeight = FontWeight.Bold)
                    Text(s("inventory.currentStock"), Modifier.weight(1.4f), fontWeight = FontWeight.Bold)
                    Text(s("inventory.threshold"), Modifier.weight(1.8f), fontWeight = FontWeight.Bold)
                }

                state.items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(2.2f)) {
                            Text(
                                enumLabel("ProductType", item.productType),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                enumLabel("StockLocation", item.location),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(weight(item.currentQuantityKg), style = MaterialTheme.typography.bodyMedium)
                            if (item.isLowStock) {
                                FlagChip(s("inventory.lowStock"))
                            }
                        }

                        WeightField(
                            label = "",
                            value = item.thresholdInput,
                            onValueChange = { vm.updateThresholdInput(item.productType, item.location, it) },
                            modifier = Modifier.weight(1.8f),
                            error = item.error,
                        )
                    }
                    HorizontalDivider()
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onCancel) { Text(s("common.cancel")) }
                SubmitButton(s("common.save"), state.saving, onClick = { vm.saveAll(onSuccess = onDone) }, tag = "thresholds.save")
            }
        }
    }
}
