package com.nexora.desktop.viewmodel.documents

import com.nexora.desktop.repository.DocumentRepository
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class AttachDocumentViewModelTest {

    @TempDir
    lateinit var dir: Path

    @Test
    @DisplayName("FR-DOC-01 NFR-SEC-03 file over10 mb is rejected before upload")
    fun fileOver10Mb_isRejectedBeforeUpload() = vmTest { server ->
        server.onJson(
            "POST /documents",
            """{"id":"d0c1","documentType":"PURCHASE_BILL","fileName":"bill.pdf","contentType":"application/pdf","sizeBytes":4,"referenceType":"PURCHASE","referenceId":"8a1b"}""",
            201,
        )
        val vm = AttachDocumentViewModel(DocumentRepository(server.client()), "PURCHASE", "8a1b")
        assertEquals("PURCHASE_BILL", vm.form.value.documentType) // suggested from the reference type

        // 10 MB + 1 byte: refused locally, nothing is sent.
        val big = dir.resolve("scan.pdf")
        RandomAccessFile(big.toFile(), "rw").use { it.setLength(AttachDocumentViewModel.MAX_BYTES + 1) }
        vm.chooseFile(big)
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("The file is larger than 10 MB.", vm.status.value.fieldErrors["file"])
        assertTrue(server.requests.isEmpty())

        // A wrong type is refused too.
        val text = Files.writeString(dir.resolve("notes.txt"), "hello")
        vm.chooseFile(text)
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("Only PDF, JPEG and PNG files can be attached.", vm.status.value.fieldErrors["file"])
        assertTrue(server.requests.isEmpty())

        // A small PDF is uploaded as multipart with the reference.
        val ok = Files.writeString(dir.resolve("bill.pdf"), "%PDF")
        vm.chooseFile(ok)
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(1, server.count("POST /documents"))
        assertTrue(server.last("POST /documents").contentType!!.startsWith("multipart/form-data"))
        assertEquals("d0c1", vm.status.value.result?.id)
    }

    @Test
    @DisplayName("E-DOC-05 share document and E-DOC-06 extract OCR")
    fun shareDocument_andExtractOcr() = vmTest { server ->
        server.onJson("POST /documents/d0c1/share", "{}", 202)
        server.onJson(
            "POST /documents/ocr-extractions",
            """{"documentId":"d0c1","suggested":{"orderedWeightKg":250.000,"ratePerKg":800.00}}""",
        )

        val repo = DocumentRepository(server.client())
        val shareRes = repo.share("d0c1", "pty1", "Here is your bill")
        assertTrue(shareRes is com.nexora.desktop.repository.ApiResult.Success)
        assertEquals(1, server.count("POST /documents/d0c1/share"))

        val ocrRes = repo.extractOcr("d0c1")
        assertTrue(ocrRes is com.nexora.desktop.repository.ApiResult.Success)
        assertEquals("d0c1", (ocrRes as com.nexora.desktop.repository.ApiResult.Success).value.documentId)
        assertEquals(1, server.count("POST /documents/ocr-extractions"))
    }
}
