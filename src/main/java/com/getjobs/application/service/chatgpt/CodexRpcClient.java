package com.getjobs.application.service.chatgpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Codex 官方 stdio JSON-RPC 传输，不读取或向前端暴露任何 token。 */
public final class CodexRpcClient implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Process process;
    private final BufferedWriter writer;
    private final Consumer<JsonNode> notifications;
    private volatile boolean connected = true;

    public CodexRpcClient(Process process, Consumer<JsonNode> notifications) {
        this.process = process;
        this.notifications = notifications;
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread.ofVirtual().name("getjobs-codex-rpc").start(this::read);
        // 不保留 stderr：上游诊断可能包含提示词或 OAuth URL。
        Thread.ofVirtual().start(() -> {
            try (var stream = process.getErrorStream()) { stream.transferTo(OutputStream.nullOutputStream()); }
            catch (IOException ignored) { }
        });
    }

    public boolean isAlive() { return connected && process.isAlive(); }

    public JsonNode request(String method, Object params, Duration timeout) {
        long id = sequence.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            if (!connected) throw new IOException("Codex disconnected");
            ObjectNode message = json.createObjectNode().put("id", id).put("method", method);
            message.set("params", json.valueToTree(params));
            write(message);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ChatGPT 请求已取消", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("ChatGPT 请求超时，请检查网络或重新登录", e);
        } catch (Exception e) {
            throw new IllegalStateException("Codex 请求失败（" + method + "），请检查登录状态、Codex 版本和网络", e);
        } finally {
            pending.remove(id);
        }
    }

    public void notify(String method) {
        write(json.createObjectNode().put("method", method));
    }

    private synchronized void write(JsonNode message) {
        try {
            writer.write(json.writeValueAsString(message));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Codex 连接已断开", e);
        }
    }

    private void read() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode message = json.readTree(line);
                if (message.has("method") && message.has("id")) {
                    // 此集成只生成文本，不代用户批准命令、文件操作或其他工具调用。
                    ObjectNode reply = json.createObjectNode();
                    reply.set("id", message.get("id"));
                    reply.putObject("error").put("code", -32601).put("message", "Get Jobs does not support tool execution");
                    write(reply);
                } else if (message.has("id")) {
                    var future = pending.get(message.path("id").asLong());
                    if (future != null) {
                        if (message.has("error")) future.completeExceptionally(new IOException("Codex RPC error"));
                        else future.complete(message.path("result"));
                    }
                } else if (message.has("method")) {
                    notifications.accept(message);
                }
            }
        } catch (Exception ignored) {
            // 由 pending futures 和断开通知给调用方稳定、无凭证的错误。
        } finally {
            connected = false;
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Codex disconnected")));
            notifications.accept(json.createObjectNode().put("method", "transport/closed"));
        }
    }

    @Override public void close() {
        connected = false;
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Codex disconnected")));
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try { if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
    }
}
