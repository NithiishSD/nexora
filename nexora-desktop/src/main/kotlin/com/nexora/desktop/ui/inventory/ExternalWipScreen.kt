package com.nexora.desktop.ui.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nexora.desktop.ui.components.FigureTile
import com.nexora.desktop.ui.components.LoadBanner
import com.nexora.desktop.ui.components.ReloadOn
import com.nexora.desktop.ui.components.ScreenHeader
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.util.IndianFormat.weight
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.inventory.ExternalWipViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Screen

/** S-INV-03 Material with manufacturers, grouped by manufacturer then job. */
@Composable
fun ExternalWipScreen(nav: NavigationViewModel, reloadTick: Int) {
    val vm: ExternalWipViewModel = screenViewModel()
    val data by vm.data.collectAsState()
    ReloadOn(reloadTick, vm::reload)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(s("inventory.withManufacturers"), onBack = nav::back)
        LoadBanner(data.error, data.lastUpdated, vm::reload)
        val d = data.value ?: return@Column
        FigureTile(s("inventory.totalOutside"), weight(d.totalOutsideKg))
        d.manufacturers.forEach { m ->
            Section(s("inventory.manufacturerLine", m.manufacturer.name, weight(m.issuedKg), weight(m.receivedKg), weight(m.wastageKg), weight(m.outsideKg))) {
                StaticTable(
                    listOf(s("job.number"), s("common.status"), s("job.issued"), s("job.receivedKg"), s("job.wastage"), s("job.outside")),
                    m.jobs.map { listOf(it.jobNumber, enumLabel("JobStatus", it.status), weight(it.issuedKg), weight(it.receivedKg), weight(it.wastageKg), weight(it.outsideKg)) },
                    numeric = setOf(2, 3, 4, 5),
                    onRowClick = { i -> nav.push(Screen.JobDetail(m.jobs[i].jobId)) },
                )
            }
        }
    }
}
