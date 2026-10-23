package com.nexora.desktop.support

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.AuthSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import kotlinx.coroutines.CoroutineDispatcher
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** A request the mock server received, with its body as text for assertions. */
data class Recorded(val method: String, val path: String, val query: Map<String, String>, val body: String?, val headers: Map<String, String>, val contentType: String? = null)

/**
 * A tiny fake backend for tests: routes are `"GET /parties"` (path under /api/v1, or an /actuator path) mapped to
 * handlers that return the JSON documented in 05-API-SPEC. Every request is recorded.
 */
class MockServer(private val dispatcher: CoroutineDispatcher? = null) {
    private val routes = mutableMapOf<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()
    val requests = mutableListOf<Recorded>()

    /** Sets the handler of a route; setting it again replaces the previous one. */
    fun on(route: String, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        routes[route] = handler
    }

    fun onJson(route: String, body: String, status: Int = 200) = on(route) { json(body, status) }

    fun count(route: String): Int = requests.count { "${it.method} ${it.path}" == route }

    fun last(route: String): Recorded = requests.last { "${it.method} ${it.path}" == route }

    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        val path = request.url.encodedPath.removePrefix("/api/v1")
        val key = "${request.method.value} $path"
        requests += Recorded(
            method = request.method.value,
            path = path,
            query = request.url.parameters.entries().associate { it.key to it.value.first() },
            body = (request.body as? TextContent)?.text ?: (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString(),
            headers = request.headers.entries().associate { it.key to it.value.first() },
            contentType = request.body.contentType?.toString(),
        )
        val route = routes[key]
        if (route == null) problem(404, "RESOURCE_NOT_FOUND", "No route $key") else route(this, request)
    }

    /**
     * The engine runs its handlers on [dispatcher] when given: view-model tests pass the test dispatcher so the
     * whole request happens in virtual time and `advanceUntilIdle()` waits for it.
     */
    fun engine() = MockEngine(MockEngineConfig().apply {
        addHandler(handler)
        this@MockServer.dispatcher?.let { dispatcher = it }
    })

    fun client(session: AuthSession = testSession(), timeoutMillis: Long = ApiClient.REQUEST_TIMEOUT_MILLIS) =
        ApiClient(engine(), { "https://shop.example.com" }, session, timeoutMillis)
}

fun MockRequestHandleScope.json(body: String, status: Int = 200): HttpResponseData =
    respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))

/** A ProblemDetail error body exactly as in 05 section 1.5. */
fun MockRequestHandleScope.problem(status: Int, code: String, detail: String = "Error.", errors: String = ""): HttpResponseData {
    val errorPart = if (errors.isEmpty()) "" else ""","errors":$errors"""
    return json(
        """{"type":"about:blank","title":"Error","status":$status,"detail":"$detail","code":"$code","timestamp":"2026-10-07T10:15:30Z"$errorPart}""",
        status,
    )
}
