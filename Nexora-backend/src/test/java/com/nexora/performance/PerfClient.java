package com.nexora.performance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// A real HTTP client against the application on its random port, as the desktop client calls it: bearer token,
// JSON bodies, an Idempotency-Key on every POST. Each call reports its own duration.
final class PerfClient {

    record Response(int status, String body, long nanos) {
    }

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = new JsonMapper();
    private final String base;
    private String token;

    PerfClient(int port) {
        this.base = "http://localhost:" + port;
    }

    void login(String username, String password) {
        token = expect(send("POST", "/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", false), 200)
                .get("accessToken").asString();
    }

    JsonNode get(String path) {
        return expect(send("GET", path, null, false), 200);
    }

    JsonNode post(String path, String body) {
        Response r = send("POST", path, body, true);
        if (r.status() / 100 != 2) {
            throw new IllegalStateException("POST " + path + " -> " + r.status() + " " + r.body());
        }
        return r.body().isEmpty() ? null : json.readTree(r.body());
    }

    Response send(String method, String path, String body, boolean idempotencyKey) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
        if (token != null && !path.startsWith("/api/v1/auth/")) {
            request.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey) {
            request.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), System.nanoTime() - start);
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private JsonNode expect(Response r, int status) {
        if (r.status() != status) {
            throw new IllegalStateException("expected " + status + " but got " + r.status() + ": " + r.body());
        }
        return json.readTree(r.body());
    }

    // The 95th percentile of the durations, in milliseconds.
    static long p95Millis(List<Long> nanos) {
        List<Long> sorted = nanos.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000;
    }
}
