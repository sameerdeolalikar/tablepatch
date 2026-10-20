package tablepatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loopback-only UI adapter. Uploaded tables stay in memory; exports are browser downloads. */
public final class Web {
    private Web() {}
    private static final int LIMIT = 4_000_000;

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    private static String rows(List<List<String>> values) {
        return "[" + String.join(",", values.stream().map(row ->
            "[" + String.join(",", row.stream().map(Web::quote).toList()) + "]").toList()) + "]";
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid form");
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (fields.putIfAbsent(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null)
                throw new IllegalArgumentException("repeated field");
        }
        return fields;
    }

    static String compare(Map<String, String> fields) {
        String key = fields.getOrDefault("key", "");
        Table before = Table.parse(fields.getOrDefault("before", ""), key);
        Table after = Table.parse(fields.getOrDefault("after", ""), key);
        if (before.rows().size() > 2000 || after.rows().size() > 2000)
            throw new IllegalArgumentException("The visual workspace supports up to 2,000 rows per table. Use the CLI for larger files.");
        String patch = Patch.compare(before, after);
        return "{\"before\":" + rows(Csv.parse(before.csv())) + ",\"after\":"
            + rows(Csv.parse(after.csv())) + ",\"patch\":" + quote(patch) + "}";
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }

    private static void handle(HttpExchange exchange, int port) throws IOException {
        try {
            String host = "127.0.0.1:" + port;
            if (!host.equals(exchange.getRequestHeaders().getFirst("Host")))
                throw new IllegalArgumentException("Use the printed loopback URL.");
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestMethod().equals("GET") && List.of("/", "/app.js", "/style.css").contains(path)) {
                String file = path.equals("/") ? "/web/index.html" : "/web" + path;
                try (var stream = Web.class.getResourceAsStream(file)) {
                    if (stream == null) throw new IOException("UI assets are missing; rebuild the JAR.");
                    String type = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                    send(exchange, 200, type, stream.readAllBytes()); return;
                }
            }
            if (!exchange.getRequestMethod().equals("POST") || !path.equals("/api/compare"))
                throw new IllegalArgumentException("unknown route");
            if (!("http://" + host).equals(exchange.getRequestHeaders().getFirst("Origin"))
                || !"TablePatch".equals(exchange.getRequestHeaders().getFirst("X-Requested-With")))
                throw new IllegalArgumentException("Request must come from the local workspace.");
            byte[] bytes = exchange.getRequestBody().readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IllegalArgumentException("Upload exceeds the 4 MB encoded request limit.");
            send(exchange, 200, "application/json", compare(form(new String(bytes, StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            send(exchange, 400, "application/json", ("{\"error\":" + quote(error.getMessage()) + "}").getBytes(StandardCharsets.UTF_8));
        } finally { exchange.close(); }
    }

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/", exchange -> handle(exchange, port));
        server.start();
        System.out.println("TablePatch workspace: http://127.0.0.1:" + port);
    }
}
