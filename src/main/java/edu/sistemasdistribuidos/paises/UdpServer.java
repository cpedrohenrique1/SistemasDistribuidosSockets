package edu.sistemasdistribuidos.paises;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UdpServer {

    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 4096;
    private final Map<String, SocketAddress> clients = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private Country targetCountry;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        new UdpServer().start();
    }

    public void start() throws Exception {
        chooseTargetCountry();

        socket = new DatagramSocket(SERVER_PORT);
        System.out.println("[SERVER] Listening on port " + SERVER_PORT + ". Game started with: " + targetCountry.name);
        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while (true) {
            try {
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                SocketAddress addr = packet.getSocketAddress();
                String key = ((InetSocketAddress) addr).getAddress().getHostAddress() + ":" + ((InetSocketAddress) addr).getPort();
                new Thread(() -> handleMessage(msg, key, addr)).start();
            } catch (IOException e) {
                System.err.println("[SERVER] Error receiving packet: " + e.getMessage());
            }
        }
    }

    private void chooseTargetCountry() {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("Digite o país que deseja iniciar o jogo (ex: Brazil): ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            Country c = fetchCountryByName(input, true);
            if (c != null) {
                targetCountry = c;
                System.out.println("[SERVER] Country set: " + c.name);
                break;
            } else {
                System.out.println("[SERVER] Country not found. Tente novamente.");
            }
        }
    }

    private void handleMessage(String message, String clientKey, SocketAddress addr) {
        try {
            if (message.equalsIgnoreCase("JOIN")) {
                clients.put(clientKey, addr);
                broadcast("[SERVER] Player joined: " + clientKey);
                sendTo(addr, "[SERVER] Welcome! Start guessing.");
            } else {
                String guess = message.toUpperCase().startsWith("GUESS:") ? message.substring(6).trim() : message;
                if (!guess.isEmpty()) processGuess(guess, clientKey);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error handling message: " + e.getMessage());
        }
    }

    private void processGuess(String guess, String clientKey) {
        broadcast("[SERVER] Player " + clientKey + " guessed: " + guess);

        Country guessed = fetchCountryByName(guess, false);
        if (guessed == null) {
            broadcast("[SERVER] País não encontrado: " + guess);
            return;
        }

        boolean nameOk = normalize(guessed.name).equalsIgnoreCase(normalize(targetCountry.name));
        if (nameOk) {
            broadcast("[SERVER] 🎉 CONGRATULATIONS! Player " + clientKey + " acertou: " + guessed.name);
            broadcast("[SERVER] Country info:\n" + formatCountryFull(guessed));
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("Relatório comparativo (chute por ").append(clientKey).append("):\n");

        // País
        report.append("País: ").append(guessed.name).append(" - incorreto\n");

        // Continente
        report.append("Continente: ").append(guessed.region)
              .append(guessed.region.equalsIgnoreCase(targetCountry.region) ? " - correto" : " - incorreto").append("\n");

        // Capital
        String cap = guessed.capital == null ? "—" : guessed.capital;
        report.append("Capital: ").append(cap)
              .append(Objects.equals(cap, targetCountry.capital) ? " - correto" : " - incorreto").append("\n");

        // Área
        report.append("Área (km²): ").append(formatNumberRelation(guessed.area, targetCountry.area)).append("\n");

        // População
        report.append("População: ").append(formatNumberRelation(guessed.population, targetCountry.population)).append("\n");

        // Línguas
        String langs = guessed.languages == null ? "—" : guessed.languages;
        boolean langsOk = hasCommonLanguage(langs, targetCountry.languages);
        report.append("Línguas: ").append(langs).append(langsOk ? " - correto" : " - incorreto").append("\n");

        broadcast(report.toString());
    }

    private String formatNumberRelation(double guess, double target) {
        if (Double.isNaN(guess) || Double.isNaN(target)) return "—";
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt", "BR"));
        nf.setMaximumFractionDigits(0);
        String guessStr = nf.format((long) guess);
        if (Math.abs(guess - target) < 1e-6) return "= " + guessStr;
        return (guess < target ? "> " : "< ") + guessStr;
    }

    private boolean hasCommonLanguage(String a, String b) {
        if (a == null || b == null) return false;
        Set<String> A = splitToSet(a);
        Set<String> B = splitToSet(b);
        for (String x : A) if (B.contains(x)) return true;
        return false;
    }

    private Set<String> splitToSet(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) out.add(p.trim().toLowerCase());
        return out;
    }

    private void sendTo(SocketAddress addr, String msg) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket dp = new DatagramPacket(data, data.length, (InetSocketAddress) addr);
            socket.send(dp);
        } catch (IOException e) {
            System.err.println("[SERVER] Error sending to " + addr + ": " + e.getMessage());
        }
    }

    private void broadcast(String msg) {
        System.out.println("[BROADCAST] " + msg.replace("\n", " | "));
        for (SocketAddress addr : clients.values()) sendTo(addr, msg);
    }

    // --- HTTP + parsing ---
    private Country fetchCountryByName(String name, boolean fullText) {
        try {
            String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String url = "https://restcountries.com/v3.1/name/" + q + (fullText ? "?fullText=true" : "");
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String body = resp.body();
            String firstObj = extractFirstJsonObject(body);
            return firstObj == null ? null : parseCountry(firstObj);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFirstJsonObject(String json) {
        int start = json.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    private Country parseCountry(String obj) {
        Country c = new Country();
        c.name = regexFirst(obj, "\"common\"\\s*:\\s*\"([^\"]+)\"");
        if (c.name == null) c.name = regexFirst(obj, "\"official\"\\s*:\\s*\"([^\"]+)\"");
        c.region = regexFirst(obj, "\"region\"\\s*:\\s*\"([^\"]+)\"");
        c.capital = regexFirst(obj, "\"capital\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
        c.area = parseDouble(regexFirst(obj, "\"area\"\\s*:\\s*([0-9]+\\.?[0-9]*)"));
        c.population = parseLong(regexFirst(obj, "\"population\"\\s*:\\s*(\\d+)"));
        String langsObj = regexFirstGroup(obj, "\"languages\"\\s*:\\s*\\{([^}]*)\\}");
        if (langsObj != null) {
            Matcher m = Pattern.compile("\"[^\"]+\"\\s*:\\s*\"([^\"]+)\"").matcher(langsObj);
            List<String> langs = new ArrayList<>();
            while (m.find()) langs.add(m.group(1));
            if (!langs.isEmpty()) c.languages = String.join(", ", langs);
        }
        return c;
    }

    private double parseDouble(String s) {
        try { return s == null ? Double.NaN : Double.parseDouble(s); } catch (Exception e) { return Double.NaN; }
    }

    private long parseLong(String s) {
        try { return s == null ? -1 : Long.parseLong(s); } catch (Exception e) { return -1; }
    }

    private String regexFirst(String text, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String regexFirstGroup(String text, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private String formatCountryFull(Country c) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt","BR"));
        nf.setMaximumFractionDigits(0);
        return "Nome: " + c.name + "\n" +
               "Continente: " + (c.region == null ? "—" : c.region) + "\n" +
               "Capital: " + (c.capital == null ? "—" : c.capital) + "\n" +
               "Área (km²): " + (Double.isNaN(c.area) ? "—" : nf.format((long)c.area)) + "\n" +
               "População: " + (c.population < 0 ? "—" : nf.format(c.population)) + "\n" +
               "Línguas: " + (c.languages == null ? "—" : c.languages) + "\n";
    }

    private static class Country {
        String name;
        String region;
        String capital;
        double area;
        long population;
        String languages;
    }
}
