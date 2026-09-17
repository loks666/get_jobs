package com.getjobs.application.service.chatgpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodexRpcClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static class Harness implements AutoCloseable {
        final PipedInputStream responses = new PipedInputStream();
        final PipedOutputStream server;
        final ByteArrayOutputStream requests = new ByteArrayOutputStream();
        final CodexRpcClient client;
        Harness() throws IOException {
            server = new PipedOutputStream(responses);
            Process process = mock(Process.class);
            when(process.getInputStream()).thenReturn(responses);
            when(process.getOutputStream()).thenReturn(requests);
            when(process.getErrorStream()).thenReturn(InputStream.nullInputStream());
            when(process.descendants()).thenReturn(java.util.stream.Stream.empty());
            client = new CodexRpcClient(process, event -> { });
        }
        void send(String json) throws IOException { server.write((json + "\n").getBytes(StandardCharsets.UTF_8)); server.flush(); }
        JsonNode waitForRequest() throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < end) {
                String text = requests.toString(StandardCharsets.UTF_8);
                if (text.contains("\n")) return JSON.readTree(text.lines().findFirst().orElseThrow());
                Thread.sleep(5);
            }
            throw new AssertionError("request was not written");
        }
        @Override public void close() throws Exception { server.close(); client.close(); }
    }

    @Test void correlatesResponsesAndEscapesMultilineParameters() throws Exception {
        try (Harness h = new Harness()) {
            var call = CompletableFuture.supplyAsync(() -> h.client.request("echo", Map.of("text", "你好\n\"quoted\""), Duration.ofSeconds(2)));
            JsonNode sent = h.waitForRequest();
            assertEquals("你好\n\"quoted\"", sent.path("params").path("text").asText());
            h.send("{\"id\":999,\"result\":{\"wrong\":true}}");
            h.send("{\"id\":" + sent.path("id") + ",\"result\":{\"ok\":true}}");
            assertTrue(call.get(2, TimeUnit.SECONDS).path("ok").asBoolean());
        }
    }

    @Test void timeoutIsBoundedAndLateResponsesDoNotBreakNextRequest() throws Exception {
        try (Harness h = new Harness()) {
            assertThrows(IllegalStateException.class, () -> h.client.request("slow", Map.of(), Duration.ofMillis(30)));
            h.send("{\"id\":1,\"result\":{}}");
            h.requests.reset();
            var call = CompletableFuture.supplyAsync(() -> h.client.request("fast", Map.of(), Duration.ofSeconds(2)));
            assertEquals(2, h.waitForRequest().path("id").asLong());
            h.send("{\"id\":2,\"result\":{\"ok\":true}}");
            assertTrue(call.get(2, TimeUnit.SECONDS).path("ok").asBoolean());
        }
    }

    @Test void rpcErrorDoesNotExposeUpstreamSecrets() throws Exception {
        try (Harness h = new Harness()) {
            var call = CompletableFuture.supplyAsync(() -> h.client.request("login", Map.of(), Duration.ofSeconds(2)));
            h.waitForRequest();
            h.send("{\"id\":1,\"error\":{\"message\":\"secret-token\"}}");
            ExecutionException error = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
            assertFalse(error.getCause().getMessage().contains("secret-token"));
        }
    }

    @Test void closedTransportUnblocksPendingRequests() throws Exception {
        try (Harness h = new Harness()) {
            var call = CompletableFuture.supplyAsync(() -> h.client.request("slow", Map.of(), Duration.ofSeconds(30)));
            h.waitForRequest();
            h.server.close();
            assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
        }
    }
}
