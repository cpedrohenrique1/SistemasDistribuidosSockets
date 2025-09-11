package edu.sistemasdistribuidos.paises;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.sistemasdistribuidos.paises.models.Pais;

import java.io.IOException;
import java.lang.reflect.Type;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UdpServer {

    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 4096;
    private final Map<String, SocketAddress> clients = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private Pais targetCountry;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        new UdpServer().start();
    }

    public void start() throws Exception {
        chooseTargetCountry();

        socket = new DatagramSocket(SERVER_PORT);
        System.out.println("[SERVER] Listening on port " + SERVER_PORT + ". Game started with: " + targetCountry.getName().getCommon());
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
            Pais c = fetchCountryByName(input, true);
            if (c != null) {
                targetCountry = c;
                System.out.println("[SERVER] Country set: " + c.getName().getCommon());
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

        Pais guessed = fetchCountryByName(guess, false);
        if (guessed == null) {
            broadcast("[SERVER] País não encontrado: " + guess);
            return;
        }

        boolean nameOk = normalize(guessed.getName().getCommon()).equalsIgnoreCase(normalize(targetCountry.getName().getCommon()));
        if (nameOk) {
            broadcast("[SERVER] 🎉 CONGRATULATIONS! Player " + clientKey + " acertou: " + guessed.getName().getCommon());
            broadcast("[SERVER] Country info:\n" + formatCountryFull(guessed));
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("Relatório comparativo (chute por ").append(clientKey).append("):\n");
        report.append("País: ").append(guessed.getName().getCommon()).append(" - incorreto\n");
        report.append("Continente: ").append(guessed.getRegion())
              .append(guessed.getRegion().equalsIgnoreCase(targetCountry.getRegion()) ? " - correto" : " - incorreto").append("\n");

        String guessedCapital = getCapital(guessed);
        String targetCapital = getCapital(targetCountry);
        report.append("Capital: ").append(guessedCapital)
              .append(Objects.equals(guessedCapital, targetCapital) ? " - correto" : " - incorreto").append("\n");

        report.append("Área (km²): ").append(formatNumberRelation(guessed.getArea(), targetCountry.getArea())).append("\n");
        report.append("População: ").append(formatNumberRelation(guessed.getPopulation(), targetCountry.getPopulation())).append("\n");

        String guessedLangs = getLanguages(guessed);
        String targetLangs = getLanguages(targetCountry);
        boolean langsOk = hasCommonLanguage(guessedLangs, targetLangs);
        report.append("Línguas: ").append(guessedLangs).append(langsOk ? " - pelo menos uma correta" : " - incorreto").append("\n");

        broadcast(report.toString());
    }
    
    private String getCapital(Pais pais) {
        if (pais.getCapital() == null || pais.getCapital().length == 0) return "—";
        return pais.getCapital()[0];
    }
    
    private String getLanguages(Pais pais) {
        if (pais.getLanguages() == null || pais.getLanguages().isEmpty()) return "—";
        return String.join(", ", pais.getLanguages().values());
    }

    private String formatNumberRelation(double guess, double target) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt", "BR"));
        nf.setMaximumFractionDigits(0);
        String guessStr = nf.format((long) guess);
        if (Math.abs(guess - target) < 1e-6) return "= " + guessStr;
        return (guess < target ? "> " : "< ") + guessStr;
    }

    private boolean hasCommonLanguage(String a, String b) {
        if (a == null || b == null || a.equals("—") || b.equals("—")) return false;
        Set<String> setA = splitToSet(a);
        Set<String> setB = splitToSet(b);
        setA.retainAll(setB); // Intersecção
        return !setA.isEmpty();
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

    private Pais fetchCountryByName(String name, boolean fullText) {
        try {
            String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String url = "https://restcountries.com/v3.1/name/" + q + "?fields=name,region,capital,area,population,languages" + (fullText ? "&fullText=true" : "");
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());

            if (resp.statusCode() != 200) return null;

            Type paisListType = new TypeToken<List<Pais>>(){}.getType();
            List<Pais> paises = gson.fromJson(resp.body(), paisListType);

            return paises.isEmpty() ? null : paises.get(0);
        } catch (Exception e) {
            System.err.println("[SERVER] Error fetching country data: " + e.getMessage());
            return null;
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private String formatCountryFull(Pais c) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt","BR"));
        nf.setMaximumFractionDigits(0);
        return "Nome: " + c.getName().getCommon() + "\n" +
               "Continente: " + (c.getRegion() == null ? "—" : c.getRegion()) + "\n" +
               "Capital: " + getCapital(c) + "\n" +
               "Área (km²): " + nf.format((long)c.getArea()) + "\n" +
               "População: " + nf.format(c.getPopulation()) + "\n" +
               "Línguas: " + getLanguages(c) + "\n";
    }
}