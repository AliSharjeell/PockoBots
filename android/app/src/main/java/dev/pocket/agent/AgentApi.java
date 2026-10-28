package dev.pocket.agent;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class AgentApi {
    private final String token;
    private final int timeout;
    private volatile HttpURLConnection events;
    AgentApi(String token) { this(token,45000); }
    AgentApi(String token,int timeout) { this.token = token; this.timeout = timeout; }
    Object request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = connect(path);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod(method);
        try {
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            String text;
            try (InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                text = new String(Io.bytes(input), StandardCharsets.UTF_8);
            }
            Object result = new JSONTokener(text).nextValue();
            if (code >= 400) throw new IOException(result instanceof JSONObject ? ((JSONObject)result).optString("error", text) : text);
            return result;
        } finally { connection.disconnect(); }
    }
    JSONObject get(String path) throws Exception { return (JSONObject)request("GET", path, null); }
    interface Event { void receive(JSONObject value); }
    void listen(Event callback) throws Exception {
        HttpURLConnection connection = connect("/events"); events = connection;
        connection.setReadTimeout(0);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) if (line.startsWith("data: ")) callback.receive(new JSONObject(line.substring(6)));
        } finally { connection.disconnect(); if (events == connection) events = null; }
    }
    void closeEvents() { HttpURLConnection connection = events; if (connection != null) connection.disconnect(); }
    private HttpURLConnection connect(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:8787/api" + path).openConnection();
        connection.setConnectTimeout(3000); connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }
    static String encode(String text) { return UriEncoder.encode(text); }
    private static final class UriEncoder {
        static String encode(String text) { return android.net.Uri.encode(text); }
    }
}
