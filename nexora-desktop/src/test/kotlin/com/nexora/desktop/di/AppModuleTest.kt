package com.nexora.desktop.di

import com.nexora.desktop.support.MockServer
import com.nexora.desktop.support.testKoin
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.parametersOf

class AppModuleTest {

    @OptIn(KoinInternalApi::class)
    @Test
    @DisplayName("koin modules resolve every definition")
    fun koinModules_resolveEveryDefinition() {
        // The real modules, with the settings file, keyring and HTTP engine replaced by test doubles.
        val app = testKoin(MockServer())
        val definitions = app.koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
        assertTrue(definitions.size > 10)
        // Resolving every definition proves that each constructor finds all of its dependencies.
        // Detail view-models take the record id as a parameter; the others ignore it.
        definitions.forEach { app.koin.get<Any>(it.primaryType, it.qualifier) { parametersOf("00000000-0000-0000-0000-000000000001") } }
        app.close()
    }
}
