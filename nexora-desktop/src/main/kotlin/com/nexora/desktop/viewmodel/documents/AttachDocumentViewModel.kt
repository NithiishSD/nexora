package com.nexora.desktop.viewmodel.documents

import com.nexora.desktop.api.dto.DocumentResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.DocumentRepository
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.FormViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path

data class AttachForm(val file: Path? = null, val documentType: String = "OTHER")

/**
 * S-DOC-02 Attach document (FR-DOC-01, -02). The file type and the 10 MB limit are checked BEFORE uploading, so a
 * file that the server would refuse is never sent (NFR-SEC-03); the server still checks the content itself.
 */
class AttachDocumentViewModel(
    private val documents: DocumentRepository,
    val referenceType: String?,
    val referenceId: String?,
) : FormViewModel<AttachForm, DocumentResponse>(AttachForm(documentType = defaultType(referenceType))) {

    private val _progress = MutableStateFlow(0f)

    /** Upload progress 0..1 for the progress bar. */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun chooseFile(path: Path) = edit { it.copy(file = path) }

    override fun validate(form: AttachForm): Map<String, String> {
        val file = form.file ?: return mapOf("file" to s("document.chooseFile"))
        val problem = when {
            contentTypeOf(file) == null -> s("document.wrongType")
            Files.size(file) > MAX_BYTES -> s("document.tooLarge")
            Files.size(file) == 0L -> s("document.emptyFile")
            else -> null
        }
        return if (problem == null) emptyMap() else mapOf("file" to problem)
    }

    override suspend fun send(form: AttachForm, idempotencyKey: String): ApiResult<DocumentResponse> {
        val file = form.file!!
        return documents.upload(file, contentTypeOf(file)!!, form.documentType, referenceType, referenceId) { sent, total ->
            if (total > 0) _progress.value = sent.toFloat() / total
        }
    }

    companion object {
        /** 10 MB, the API limit (05 E-DOC-01). */
        const val MAX_BYTES = 10L * 1024 * 1024

        private val TYPES = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png")

        fun contentTypeOf(file: Path): String? = TYPES[file.fileName.toString().substringAfterLast('.', "").lowercase()]

        /** Document type suggested by what the document is attached to (06 S-DOC-02). */
        fun defaultType(referenceType: String?): String = when (referenceType) {
            "PURCHASE" -> "PURCHASE_BILL"
            "ORDER" -> "SALES_BILL"
            "EXPENSE" -> "EXPENSE_RECEIPT"
            else -> "OTHER"
        }
    }
}
