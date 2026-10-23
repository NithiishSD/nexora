package com.nexora.desktop.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.desktop.api.ApiError
import com.nexora.desktop.api.dto.DocumentResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.DocumentRepository
import com.nexora.desktop.ui.components.DateRangeFilter
import com.nexora.desktop.ui.components.Dropdown
import com.nexora.desktop.ui.components.ErrorBanner
import com.nexora.desktop.ui.components.FieldError
import com.nexora.desktop.ui.components.FormDialog
import com.nexora.desktop.ui.components.ListScreen
import com.nexora.desktop.ui.components.Section
import com.nexora.desktop.ui.components.StaticTable
import com.nexora.desktop.ui.components.TableColumn
import com.nexora.desktop.ui.components.enumLabel
import com.nexora.desktop.ui.components.screenViewModel
import com.nexora.desktop.ui.components.textColumn
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.FileDialogs
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import com.nexora.desktop.viewmodel.documents.AttachDocumentViewModel
import com.nexora.desktop.viewmodel.shell.NavigationViewModel
import com.nexora.desktop.viewmodel.shell.Overlay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Desktop
import java.nio.file.Files
import java.time.Clock

private val DOCUMENT_TYPES = listOf("PURCHASE_BILL", "SALES_BILL", "BANK_STATEMENT", "EXPENSE_RECEIPT", "OTHER")

/** S-DOC-01 list view-model. */
class DocumentListViewModel(repository: DocumentRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<DocumentResponse>(ListQuery(filters = filters), clock, { repository.list(it) })

/** Downloads a document and opens it with the computer's default application (06 S-DOC-01 Open). */
private suspend fun open(repository: DocumentRepository, doc: DocumentResponse): ApiError? = when (val r = repository.content(doc.id)) {
    is ApiResult.Success -> {
        val file = Files.createTempFile("smartsilk-", "-" + doc.fileName)
        Files.write(file, r.value)
        runCatching { Desktop.getDesktop().open(file.toFile()) }
        null
    }
    is ApiResult.Failure -> r.error
}

private suspend fun saveAs(repository: DocumentRepository, doc: DocumentResponse): ApiError? = when (val r = repository.content(doc.id)) {
    is ApiResult.Success -> { FileDialogs.save(s("document.saveAs"), doc.fileName, r.value); null }
    is ApiResult.Failure -> r.error
}

/** S-DOC-01 Documents. */
@Composable
fun DocumentsScreen(nav: NavigationViewModel, reloadTick: Int, searchTick: Int) {
    val vm: DocumentListViewModel = screenViewModel()
    val state by vm.state.collectAsState()
    val repository: DocumentRepository = koinInject()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<ApiError?>(null) }
    ListScreen(
        title = s("nav.documents"),
        vm = vm,
        columns = listOf(
            textColumn<DocumentResponse>(s("document.uploadedAt"), 1.2f) { Dates.formatIsoTimestamp(it.createdAt) },
            textColumn(s("document.type"), 1.1f) { enumLabel("DocumentType", it.documentType) },
            textColumn(s("document.fileName"), 2f) { it.fileName },
            textColumn(s("document.size"), 0.8f, numeric = true) { s("document.kb", (it.sizeBytes + 1023) / 1024) },
            textColumn(s("inventory.reference"), 1f) { enumLabel("DocumentReferenceType", it.referenceType) },
            TableColumn(s("document.actions"), 1.6f) { d ->
                Row(Modifier.weight(1.6f)) {
                    TextButton(onClick = { scope.launch { error = open(repository, d) } }) { Text(s("document.open")) }
                    TextButton(onClick = { scope.launch { error = saveAs(repository, d) } }) { Text(s("document.saveAs")) }
                }
            },
        ),
        emptyMessage = s("document.empty"),
        reloadTick = reloadTick,
        searchTick = searchTick,
        newLabel = s("document.attach"),
        onNew = { nav.show(Overlay.AttachDocument()) },
        header = { error?.let { ErrorBanner(it, onDismiss = { error = null }) } },
        filters = { DateRangeFilter(state.query.filters, vm::setFilter) },
    )
}

/** Documents of one record (purchase, order, payment detail), with an attach button. */
@Composable
fun DocumentsSection(nav: NavigationViewModel, referenceType: String, referenceId: String, reloadTick: Int) {
    val repository: DocumentRepository = koinInject()
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<DocumentResponse>>(emptyList()) }
    LaunchedEffect(referenceId, reloadTick) {
        docs = repository.list(ListQuery(size = 100, filters = mapOf("referenceType" to referenceType, "referenceId" to referenceId))).getOrNull()?.content.orEmpty()
    }
    Section(s("nav.documents")) {
        StaticTable(
            listOf(s("document.fileName"), s("document.type"), s("document.uploadedAt")),
            docs.map { listOf(it.fileName, enumLabel("DocumentType", it.documentType), Dates.formatIsoTimestamp(it.createdAt)) },
            onRowClick = { i -> scope.launch { open(repository, docs[i]) } },
        )
        OutlinedButton(onClick = { nav.show(Overlay.AttachDocument(referenceType, referenceId)) }) { Text(s("document.attach")) }
    }
}

/**
 * S-DOC-02 Attach document: native file chooser, type and 10 MB checked before upload, upload progress bar.
 */
@Composable
fun AttachDocumentDialog(referenceType: String?, referenceId: String?, onDone: () -> Unit) {
    val vm: AttachDocumentViewModel = screenViewModel(referenceType, referenceId)
    val form by vm.form.collectAsState()
    val status by vm.status.collectAsState()
    val progress by vm.progress.collectAsState()
    LaunchedEffect(status.result) { if (status.result != null) onDone() }
    FormDialog(s("overlay.AttachDocument"), s("document.upload"), status.submitting, status.error, vm::save, onDone) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { FileDialogs.chooseOpenFile(s("document.chooseFile"))?.let(vm::chooseFile) }) { Text(s("document.chooseFile")) }
            Text(form.file?.fileName?.toString() ?: s("document.noFile"))
        }
        Text(s("document.allowed"))
        status.fieldErrors["file"]?.let { FieldError(it) }
        Dropdown(s("document.type"), DOCUMENT_TYPES.map { it to enumLabel("DocumentType", it) }, form.documentType, { v -> vm.edit { it.copy(documentType = v) } })
        if (status.submitting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}
