package com.nexora.desktop.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** "Now" in every view-model test: 8 October 2026, 10:00 in India. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-08T04:30:00Z"), ZoneOffset.UTC)

/**
 * Runs a view-model test in virtual time: `viewModelScope` (Dispatchers.Main), the mock server and the test body
 * share one test dispatcher, so delays such as the 300 ms search debounce take no real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun vmTest(block: suspend TestScope.(MockServer) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) { block(MockServer(dispatcher)) }
    } finally {
        Dispatchers.resetMain()
    }
}
