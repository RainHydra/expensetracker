import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Personal Expense Tracking and Budget Management System
 * Backend: Java built-in HTTP server (no external libraries).
 * Serves the frontend from /public and a small REST API under /api.
 *
 * Run from the project root so that "public" and "data" folders are found.
 */
public class Main {
    private static final DataStore store = new DataStore("data");
    private static final Path PUBLIC_DIR = Paths.get("public").toAbsolutePath().normalize();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/transactions", Main::handleTransactions);
        server.createContext("/api/budgets", Main::handleBudgets);
        server.createContext("/api/summary", Main::handleSummary);
        server.createContext("/", Main::handleStatic);
        server.start();
        System.out.println("Expense Tracker running at http://localhost:" + port);
    }

    // ---------------------------------------------------------------- API handlers

    private static void handleTransactions(HttpExchange ex) throws IOException {
        try {
            Map<String, String> q = parseForm(ex.getRequestURI().getRawQuery());
            switch (ex.getRequestMethod()) {
                case "GET": {
                    List<Transaction> list = store.getTransactions(q.get("month"));
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(list.get(i).toJson());
                    }
                    sb.append("]");
                    sendJson(ex, 200, sb.toString());
                    break;
                }
                case "POST": {
                    Map<String, String> f = parseForm(readBody(ex));
                    String type = f.getOrDefault("type", "").toUpperCase();
                    String category = clean(f.get("category"));
                    String description = clean(f.get("description"));
                    String date = f.getOrDefault("date", "").trim();
                    double amount;
                    try {
                        amount = Double.parseDouble(f.getOrDefault("amount", ""));
                    } catch (NumberFormatException e) {
                        sendError(ex, 400, "Amount must be a number");
                        return;
                    }
                    if (!type.equals("INCOME") && !type.equals("EXPENSE")) {
                        sendError(ex, 400, "Type must be INCOME or EXPENSE");
                    } else if (category.isEmpty()) {
                        sendError(ex, 400, "Category is required");
                    } else if (!(amount > 0) || Double.isInfinite(amount)) {
                        sendError(ex, 400, "Amount must be greater than 0");
                    } else if (!isValidDate(date)) {
                        sendError(ex, 400, "Date must be a valid yyyy-MM-dd date");
                    } else {
                        Transaction t = store.addTransaction(type, category, Math.round(amount * 100) / 100.0, description, date);
                        sendJson(ex, 201, t.toJson());
                    }
                    break;
                }
                case "DELETE": {
                    int id;
                    try {
                        id = Integer.parseInt(q.getOrDefault("id", ""));
                    } catch (NumberFormatException e) {
                        sendError(ex, 400, "Invalid id");
                        return;
                    }
                    if (store.deleteTransaction(id)) sendJson(ex, 200, "{\"deleted\":true}");
                    else sendError(ex, 404, "Transaction not found");
                    break;
                }
                default:
                    sendError(ex, 405, "Method not allowed");
            }
        } catch (Exception e) {
            sendError(ex, 500, "Server error: " + e.getMessage());
        }
    }

    private static void handleBudgets(HttpExchange ex) throws IOException {
        try {
            Map<String, String> q = parseForm(ex.getRequestURI().getRawQuery());
            switch (ex.getRequestMethod()) {
                case "GET": {
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (Map.Entry<String, Double> e : store.getBudgets().entrySet()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("\"").append(Json.escape(e.getKey())).append("\":").append(e.getValue());
                    }
                    sb.append("}");
                    sendJson(ex, 200, sb.toString());
                    break;
                }
                case "POST": {
                    Map<String, String> f = parseForm(readBody(ex));
                    String category = clean(f.get("category"));
                    double limit;
                    try {
                        limit = Double.parseDouble(f.getOrDefault("limit", ""));
                    } catch (NumberFormatException e) {
                        sendError(ex, 400, "Limit must be a number");
                        return;
                    }
                    if (category.isEmpty()) {
                        sendError(ex, 400, "Category is required");
                    } else if (!(limit > 0) || Double.isInfinite(limit)) {
                        sendError(ex, 400, "Limit must be greater than 0");
                    } else {
                        store.setBudget(category, limit);
                        sendJson(ex, 200, "{\"saved\":true}");
                    }
                    break;
                }
                case "DELETE": {
                    String category = clean(q.get("category"));
                    if (store.deleteBudget(category)) sendJson(ex, 200, "{\"deleted\":true}");
                    else sendError(ex, 404, "Budget not found");
                    break;
                }
                default:
                    sendError(ex, 405, "Method not allowed");
            }
        } catch (Exception e) {
            sendError(ex, 500, "Server error: " + e.getMessage());
        }
    }

    /** Monthly summary: totals plus per-category spending compared with budgets. */
    private static void handleSummary(HttpExchange ex) throws IOException {
        try {
            if (!ex.getRequestMethod().equals("GET")) {
                sendError(ex, 405, "Method not allowed");
                return;
            }
            Map<String, String> q = parseForm(ex.getRequestURI().getRawQuery());
            String month = q.get("month");
            if (month == null || !month.matches("\\d{4}-\\d{2}")) month = YearMonth.now().toString();

            double income = 0, expense = 0;
            Map<String, Double> spent = new TreeMap<>();
            for (Transaction t : store.getTransactions(month)) {
                if (t.isExpense()) {
                    expense += t.getAmount();
                    spent.merge(t.getCategory(), t.getAmount(), Double::sum);
                } else {
                    income += t.getAmount();
                }
            }
            Map<String, Double> budgets = store.getBudgets();
            Set<String> cats = new TreeSet<>(spent.keySet());
            cats.addAll(budgets.keySet());

            StringBuilder sb = new StringBuilder();
            sb.append("{\"month\":\"").append(month).append("\"");
            sb.append(",\"income\":").append(round(income));
            sb.append(",\"expense\":").append(round(expense));
            sb.append(",\"balance\":").append(round(income - expense));
            sb.append(",\"categories\":[");
            boolean first = true;
            for (String c : cats) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"category\":\"").append(Json.escape(c)).append("\"")
                  .append(",\"spent\":").append(round(spent.getOrDefault(c, 0.0)))
                  .append(",\"budget\":").append(budgets.containsKey(c) ? budgets.get(c) : "null")
                  .append("}");
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        } catch (Exception e) {
            sendError(ex, 500, "Server error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- static files

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = PUBLIC_DIR.resolve(path.substring(1)).normalize();
        if (!file.startsWith(PUBLIC_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(ex, 200, contentType(file.getFileName().toString()), Files.readAllBytes(file));
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- helpers

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Parses "a=1&b=2" (URL-encoded) into a map. */
    private static Map<String, String> parseForm(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }

    /** Trims and strips tabs/newlines so values never break the data file format. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim();
    }

    private static boolean isValidDate(String s) {
        try {
            LocalDate.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendError(HttpExchange ex, int code, String message) throws IOException {
        sendJson(ex, code, "{\"error\":\"" + Json.escape(message) + "\"}");
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
