package dev.pocket.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Loopback HTTP server on 127.0.0.1:18890 exposing browser automation tools
 * directly to local backend bots without requiring OS accessibility or ADB.
 */
public class BotBrowserServer extends Thread {
    private static final String TAG = "BotBrowserServer";
    public static final int DEFAULT_PORT = 18890;

    private static volatile BotBrowserServer activeServer;

    public static synchronized void startServer(Context context) {
        if (activeServer != null && activeServer.isAlive()) return;
        try {
            activeServer = new BotBrowserServer(context, DEFAULT_PORT);
            activeServer.start();
            Log.i(TAG, "BotBrowserServer started on port " + DEFAULT_PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BotBrowserServer", e);
        }
    }

    public static synchronized void stopServer() {
        if (activeServer != null) {
            activeServer.shutdown();
            activeServer = null;
        }
    }

    private final Context context;
    private final int port;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private volatile ServerSocket serverSocket;
    private volatile boolean running = true;

    public BotBrowserServer(Context context, int port) {
        super("bot-browser-server");
        this.context = context.getApplicationContext();
        this.port = port;
    }

    private String getExpectedToken() {
        try {
            File tokenFile = new File(context.getFilesDir(), "state/token.json");
            if (tokenFile.exists()) {
                return (String) new JSONTokener(Io.text(tokenFile)).nextValue();
            }
        } catch (Exception ignored) {}
        return "";
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);

            while (running && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(310000); // Allow long-poll for human handoff
                    workers.execute(() -> handleClient(client));
                } catch (SocketException e) {
                    if (!running) break;
                } catch (Exception e) {
                    Log.w(TAG, "Error accepting client", e);
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "Server socket failed", e);
        } finally {
            shutdown();
        }
    }

    private void handleClient(Socket socket) {
        try (InputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
            String requestLine = null;
            Map<String, String> headers = new HashMap<>();

            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    String line = lineBuf.toString(StandardCharsets.UTF_8.name()).trim();
                    lineBuf.reset();
                    if (line.isEmpty()) break; // End of headers
                    if (requestLine == null) {
                        requestLine = line;
                    } else {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                        line.substring(colon + 1).trim());
                        }
                    }
                } else if (b != '\r') {
                    lineBuf.write(b);
                }
            }

            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;

            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String path = rawPath.contains("?") ? rawPath.substring(0, rawPath.indexOf("?")) : rawPath;

            // Ping does not require token
            if (path.equals("/ping") && method.equals("GET")) {
                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("service", "pocket-bot-browser");
                res.put("activeBots", new JSONArray(BotBrowserManager.get().getActiveBotIds()));
                sendJson(out, 200, res);
                return;
            }

            // Auth check
            String expected = getExpectedToken();
            String authHeader = headers.get("authorization");
            String customToken = headers.get("x-bot-token");
            String suppliedToken = "";
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                suppliedToken = authHeader.substring(7).trim();
            } else if (customToken != null) {
                suppliedToken = customToken.trim();
            }

            if (!expected.isEmpty() && !expected.equals(suppliedToken)) {
                sendJson(out, 403, new JSONObject().put("error", "Unauthorized: Invalid bot token"));
                return;
            }

            // Read request body if present
            byte[] bodyBytes = new byte[0];
            String clHeader = headers.get("content-length");
            if (clHeader != null) {
                try {
                    int length = Integer.parseInt(clHeader);
                    if (length > 0 && length < 10 * 1024 * 1024) {
                        bodyBytes = new byte[length];
                        int totalRead = 0;
                        while (totalRead < length) {
                            int read = in.read(bodyBytes, totalRead, length - totalRead);
                            if (read == -1) break;
                            totalRead += read;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }

            JSONObject body = new JSONObject();
            if (bodyBytes.length > 0) {
                try {
                    body = new JSONObject(new String(bodyBytes, StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }

            // Route matching: /browser/:botId/...
            if (path.startsWith("/browser/")) {
                handleBrowserRoute(out, method, path, body);
            } else {
                sendJson(out, 404, new JSONObject().put("error", "Not found: " + path));
            }

        } catch (Exception e) {
            Log.w(TAG, "Request handling failed", e);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleBrowserRoute(OutputStream out, String method, String path, JSONObject body) throws Exception {
        // Path pattern: /browser/{botId}/{action}
        String[] tokens = path.split("/");
        if (tokens.length < 4) {
            sendJson(out, 400, new JSONObject().put("error", "Invalid route structure"));
            return;
        }

        String botId = tokens[2];
        String action = tokens[3];

        BotBrowserInstance browser = BotBrowserManager.get().getOrCreate(context, botId);
        if (browser == null) {
            sendJson(out, 500, new JSONObject().put("error", "Failed to create or access browser for bot " + botId));
            return;
        }

        switch (action) {
            case "state": {
                JSONObject res = new JSONObject();
                res.put("botId", botId);
                res.put("url", browser.getCurrentUrl());
                res.put("title", browser.getCurrentTitle());
                res.put("isLoading", browser.isLoading());
                res.put("progress", browser.getLoadProgress());
                res.put("waitingForHuman", browser.isWaitingForHuman());
                res.put("humanRequestReason", browser.getHumanRequestReason());
                res.put("canGoBack", browser.canGoBack());
                res.put("canGoForward", browser.canGoForward());
                res.put("lastError", browser.getLastError());
                res.put("activeTab", browser.getActiveIndex());
                res.put("tabCount", browser.getTabCount());
                res.put("tabs", browser.tabSummaries());
                sendJson(out, 200, res);
                break;
            }

            case "tabs": {
                JSONObject res = new JSONObject();
                res.put("activeTab", browser.getActiveIndex());
                res.put("tabCount", browser.getTabCount());
                res.put("tabs", browser.tabSummaries());
                sendJson(out, 200, res);
                break;
            }

            case "newtab": {
                String url = body.optString("url", "");
                BotBrowserInstance.Tab tab = browser.openTab(url);
                sendJson(out, 200, new JSONObject()
                    .put("success", true)
                    .put("tabIndex", browser.getActiveIndex())
                    .put("tabCount", browser.getTabCount())
                    .put("url", tab == null ? "" : tab.url)
                    .put("tabs", browser.tabSummaries()));
                break;
            }

            case "switchtab": {
                int index = body.optInt("index", -1);
                if (!browser.switchTab(index)) {
                    sendJson(out, 400, new JSONObject().put("error", "No tab at index " + index));
                    return;
                }
                sendJson(out, 200, new JSONObject()
                    .put("success", true)
                    .put("activeTab", browser.getActiveIndex())
                    .put("url", browser.getCurrentUrl())
                    .put("title", browser.getCurrentTitle())
                    .put("tabs", browser.tabSummaries()));
                break;
            }

            case "closetab": {
                int index = body.optInt("index", -1);
                if (!browser.closeTab(index)) {
                    sendJson(out, 400, new JSONObject().put("error", "That tab cannot be closed."));
                    return;
                }
                sendJson(out, 200, new JSONObject()
                    .put("success", true)
                    .put("activeTab", browser.getActiveIndex())
                    .put("tabCount", browser.getTabCount())
                    .put("tabs", browser.tabSummaries()));
                break;
            }

            case "navigate": {
                String url = body.optString("url");
                if (url.isEmpty()) {
                    sendJson(out, 400, new JSONObject().put("error", "Missing url parameter"));
                    return;
                }
                // A page can be opened in a tab of its own, so the one in front is left as it is.
                if (body.has("tab")) browser.switchTab(body.optInt("tab", -1));
                boolean waitForLoad = body.optBoolean("waitForLoad", true);
                long before = browser.navigationCount();
                browser.navigate(url);

                if (waitForLoad) {
                    // Wait for the load to begin before waiting for it to end. A WebView that has not
                    // been asked for anything reports neither loading nor progress, so a wait on those
                    // alone called the page settled before it had started, and the bot saw a blank page.
                    long started = System.currentTimeMillis() + 8000;
                    while (System.currentTimeMillis() < started && browser.navigationCount() == before) {
                        Thread.sleep(50);
                    }
                    int checks = 0;
                    while (checks < 50) {
                        Thread.sleep(200);
                        checks++;
                        if (!browser.isLoading() && browser.getLoadProgress() >= 90) break;
                    }
                }

                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("url", browser.getCurrentUrl());
                res.put("title", browser.getCurrentTitle());
                res.put("isLoading", browser.isLoading());
                sendJson(out, 200, res);
                break;
            }

            case "dom": {
                try {
                    String domJson = browser.extractInteractiveDom().get(8, TimeUnit.SECONDS);
                    sendRawJson(out, 200, domJson);
                } catch (Exception e) {
                    sendJson(out, 500, new JSONObject().put("error", "DOM extraction failed: " + e.getMessage()));
                }
                break;
            }

            case "click": {
                if (body.has("id")) {
                    int id = body.getInt("id");
                    boolean success = browser.clickElementById(id).get(5, TimeUnit.SECONDS);
                    sendJson(out, 200, new JSONObject().put("success", success).put("id", id));
                } else if (body.has("x") && body.has("y")) {
                    float x = (float) body.getDouble("x");
                    float y = (float) body.getDouble("y");
                    browser.tapCoordinates(x, y);
                    sendJson(out, 200, new JSONObject().put("success", true).put("x", x).put("y", y));
                } else {
                    sendJson(out, 400, new JSONObject().put("error", "Provide 'id' or coordinates 'x' and 'y'"));
                }
                break;
            }

            case "type": {
                int id = body.optInt("id", -1);
                String text = body.optString("text", "");
                boolean clear = body.optBoolean("clear", false);
                if (id <= 0) {
                    sendJson(out, 400, new JSONObject().put("error", "Missing or invalid element id"));
                    return;
                }
                boolean success = browser.inputTextById(id, text, clear).get(5, TimeUnit.SECONDS);
                sendJson(out, 200, new JSONObject().put("success", success).put("id", id));
                break;
            }

            case "scroll": {
                int dx = body.optInt("dx", 0);
                int dy = body.optInt("dy", body.optString("direction", "").equals("up") ? -400 : 400);
                browser.scroll(dx, dy);
                sendJson(out, 200, new JSONObject().put("success", true).put("dx", dx).put("dy", dy));
                break;
            }

            case "screenshot": {
                try {
                    int width = body.optInt("width", 1080);
                    int height = body.optInt("height", 1920);
                    String base64 = browser.captureScreenshotBase64(width, height).get(6, TimeUnit.SECONDS);
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    res.put("mimeType", "image/jpeg");
                    res.put("screenshot", base64);
                    sendJson(out, 200, res);
                } catch (Exception e) {
                    sendJson(out, 500, new JSONObject().put("error", "Screenshot capture failed: " + e.getMessage()));
                }
                break;
            }

            case "ask_human": {
                String reason = body.optString("reason", "Please complete this step in the browser and hand back to the bot.");
                long timeoutMs = body.optLong("timeoutMs", 300000); // 5 minutes default

                try {
                    CompletableFuture<String> future = browser.requestHumanIntervention(reason);
                    String userNotes = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    res.put("userNotes", userNotes);
                    res.put("url", browser.getCurrentUrl());
                    res.put("title", browser.getCurrentTitle());
                    sendJson(out, 200, res);
                } catch (TimeoutException e) {
                    browser.completeHumanIntervention("Timeout waiting for human assistance");
                    sendJson(out, 408, new JSONObject().put("error", "Timed out waiting for human handoff"));
                } catch (Exception e) {
                    sendJson(out, 500, new JSONObject().put("error", "Human handoff error: " + e.getMessage()));
                }
                break;
            }

            case "complete_human": {
                String userNotes = body.optString("userNotes", "Completed by user");
                browser.completeHumanIntervention(userNotes);
                sendJson(out, 200, new JSONObject().put("success", true).put("userNotes", userNotes));
                break;
            }

            case "action": {
                String cmd = body.optString("cmd", "");
                if (cmd.equals("back")) browser.goBack();
                else if (cmd.equals("forward")) browser.goForward();
                else if (cmd.equals("reload")) browser.reload();
                else {
                    sendJson(out, 400, new JSONObject().put("error", "Unknown cmd: " + cmd));
                    return;
                }
                sendJson(out, 200, new JSONObject().put("success", true).put("cmd", cmd));
                break;
            }

            default:
                sendJson(out, 404, new JSONObject().put("error", "Unknown browser action: " + action));
        }
    }

    private void sendJson(OutputStream out, int status, JSONObject json) throws IOException {
        sendRawJson(out, status, json.toString());
    }

    private void sendRawJson(OutputStream out, int status, String jsonStr) throws IOException {
        byte[] payload = jsonStr.getBytes(StandardCharsets.UTF_8);
        String statusText = status == 200 ? "OK" : status == 400 ? "Bad Request" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 408 ? "Request Timeout" : "Internal Server Error";
        String header = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: " + payload.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        workers.shutdownNow();
    }
}
