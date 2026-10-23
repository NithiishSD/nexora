package com.nexora.desktop.viewmodel.parties

import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.support.problem
import com.nexora.desktop.support.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PartyFormViewModelTest {

    @Test
    @DisplayName("FR-PARTY-01 NFR-USE-04 field errors shown under matching fields")
    fun fieldErrors_shownUnderMatchingFields() = vmTest { server ->
        server.on("POST /parties") {
            problem(400, "VALIDATION_FAILED", "Request validation failed.",
                """[{"field":"name","message":"size must be at most 200"},{"field":"phones[0].phoneNumber","message":"must match phone format"}]""")
        }
        val vm = PartyFormViewModel(PartyRepository(server.client()), null)

        // Client check first: no name and no role never reach the server.
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(setOf("name", "roles"), vm.status.value.fieldErrors.keys)
        assertEquals(0, server.count("POST /parties"))

        vm.edit { it.copy(name = "Kumar Silks") }
        vm.toggleRole("CUSTOMER")
        vm.updatePhone(0) { it.copy(number = "+91 98400 12345") }
        vm.save()
        testScheduler.advanceUntilIdle()

        // The server's errors[] land under the inputs with the same API field names.
        val status = vm.status.value
        assertEquals("VALIDATION_FAILED", status.error?.code)
        assertEquals("size must be at most 200", status.fieldErrors["name"])
        assertEquals("must match phone format", status.fieldErrors["phones[0].phoneNumber"])
        // The typed input stays.
        assertEquals("Kumar Silks", vm.form.value.name)
        assertEquals(
            """{"name":"Kumar Silks","roles":["CUSTOMER"],"state":"Tamil Nadu","phones":[{"phoneNumber":"+91 98400 12345","label":"Mobile","primary":true}]}""",
            server.last("POST /parties").body,
        )
    }

    @Test
    @DisplayName("FR-PARTY-17 customer credit days validation and payload")
    fun customerCreditDays_validationAndPayload() = vmTest { server ->
        server.onJson("POST /parties",
            """{"id":"p1","partyCode":"CUS-0001","name":"Kumar Silks","roles":["CUSTOMER"],"status":"ACTIVE","customerCreditDays":45}""")
        val vm = PartyFormViewModel(PartyRepository(server.client()), null)
        vm.edit { it.copy(name = "Kumar Silks", customerCreditDays = "400") }
        vm.toggleRole("CUSTOMER")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(setOf("customerCreditDays"), vm.status.value.fieldErrors.keys)
        assertEquals(0, server.count("POST /parties"))

        vm.edit { it.copy(customerCreditDays = "45") }
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(1, server.count("POST /parties"))
        val body = server.last("POST /parties").body.orEmpty()
        assert(body.contains("\"customerCreditDays\":45"))
    }
}
