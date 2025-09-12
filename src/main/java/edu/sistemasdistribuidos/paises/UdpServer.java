package edu.sistemasdistribuidos.paises;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import edu.sistemasdistribuidos.paises.models.Pais;
import edu.sistemasdistribuidos.paises.models.Translation;
import edu.sistemasdistribuidos.paises.services.CountryService;

public class UdpServer {

    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 4096;
    private final Map<String, SocketAddress> clients = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private Pais targetCountry;

    private final CountryService countryService = new CountryService(); // Instancia o serviço

    public static void main(String[] args) throws Exception {
        new UdpServer().start();
    }

    public void start() throws Exception {
        chooseTargetCountry();

        // Inicia o socket UDP
        socket = new DatagramSocket(SERVER_PORT);
        System.out.println("[SERVER] Ouvindo na porta " + SERVER_PORT + ". Jogo iniciado com: " + getPortugueseName(targetCountry));
        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        // Loop principal de recebimento de mensagens
        while (true) {
            try {
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                SocketAddress addr = packet.getSocketAddress();
                String key = ((InetSocketAddress) addr).getAddress().getHostAddress() + ":" + ((InetSocketAddress) addr).getPort();
                new Thread(() -> handleMessage(msg, key, addr)).start();
            } catch (IOException e) {
                if (socket.isClosed()) {
                    System.out.println("[SERVER] Socket fechado. Encerrando o servidor.");
                    break;
                }
                System.err.println("[SERVER] Erro recebimento de pacote : " + e.getMessage());
            }
        }
    }

    // Lógica para escolher o país alvo do jogo
    private void chooseTargetCountry() {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("Digite o país que deseja iniciar o jogo (ex: Brasil): ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            // Usa o serviço para buscar o país
            Pais c = countryService.findCountry(input);
            // Se encontrado, define como país alvo e sai do loop
            if (c != null) {
                targetCountry = c;
                System.out.println("[SERVER] País definido: " + getPortugueseName(c));
                break;
            } else {
                System.out.println("[SERVER] País não encontrado. Tente novamente.");
            }
        }
    }

    // Lógica para processar mensagens recebidas
    private void handleMessage(String message, String clientKey, SocketAddress addr) {
        try {
            if (message.equalsIgnoreCase("JOIN")) {
                clients.put(clientKey, addr);
                broadcast("[SERVER] Jogador entrou: " + clientKey);
                sendTo(addr, "[SERVER] Bem-vindo! Comece a adivinhar.");
            } else {
                String guess = message.toUpperCase().startsWith("GUESS:") ? message.substring(6).trim() : message;
                if (!guess.isEmpty()) processGuess(guess, clientKey);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Erro: " + e.getMessage());
        }
    }

    // Lógica para processar um palpite de país
    private void processGuess(String guess, String clientKey) {
        broadcast("[SERVER] Jogador " + clientKey + " chutou: " + guess);

        // Usa o serviço para buscar o país do palpite
        Pais guessed = countryService.findCountry(guess);
        if (guessed == null) {
            broadcast("[SERVER] País não encontrado: " + guess);
            return;
        }

        // Verifica se o palpite está correto
        boolean nameOk = normalize(guessed.getName().getCommon()).equalsIgnoreCase(normalize(targetCountry.getName().getCommon()));
        if (nameOk) {
            broadcast("[SERVER] 🎉 PARABÉNS! Jogador " + clientKey + " acertou: " + getPortugueseName(guessed));
            broadcast("[SERVER] Informações do país:\n" + formatCountryFull(guessed));
            broadcast("SHUTDOWN");
            System.out.println("[SERVER] Jogo encerrado. Desligando...");
            socket.close();
            System.exit(0);
            return;
        }

        // Gera o relatório comparativo 
        StringBuilder report = new StringBuilder();
        report.append("Relatório comparativo (chute de ").append(clientKey).append("):\n");
        report.append("País: ").append(getPortugueseName(guessed)).append(" - incorreto\n");
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
        report.append("Digite o proximo palpite ou 'desisto' para sair.").append("\n");

        broadcast(report.toString());
    }
    
    // Auxiliares para formatação e envio de mensagens
    // Formata a capital do país, tratando casos nulos ou vazios
    private String getCapital(Pais pais) {
        if (pais.getCapital() == null || pais.getCapital().length == 0) return "—";
        return pais.getCapital()[0];
    }
    // Formata as línguas do país, tratando casos nulos ou vazios
    private String getLanguages(Pais pais) {
        if (pais.getLanguages() == null || pais.getLanguages().isEmpty()) return "—";
        return String.join(", ", pais.getLanguages().values());
    }
    // Formata a relação numérica entre o palpite e o país alvo
    private String formatNumberRelation(double guess, double target) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt", "BR"));
        nf.setMaximumFractionDigits(0);
        String guessStr = nf.format((long) guess);
        if (Math.abs(guess - target) < 1e-6) return "= " + guessStr;
        return (guess < target ? "> " : "< ") + guessStr;
    }
    // Verifica se há pelo menos uma língua em comum entre os dois países
    private boolean hasCommonLanguage(String a, String b) {
        if (a == null || b == null || a.equals("—") || b.equals("—")) return false;
        Set<String> setA = splitToSet(a);
        Set<String> setB = splitToSet(b);
        setA.retainAll(setB); // Intersecção
        return !setA.isEmpty();
    }
    // Divide uma string de línguas em um conjunto, para comparação
    private Set<String> splitToSet(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) out.add(p.trim().toLowerCase());
        return out;
    }

    // Envio de mensagens para clientes
    private void sendTo(SocketAddress addr, String msg) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket dp = new DatagramPacket(data, data.length, (InetSocketAddress) addr);
            socket.send(dp);
        } catch (IOException e) {
            System.err.println("[SERVER] Erro enviando para " + addr + ": " + e.getMessage());
        }
    }

    // Envia uma mensagem para todos os clientes conectados
    private void broadcast(String msg) {
        System.out.println("[BROADCAST] " + msg);
        //System.out.println("[BROADCAST] " + msg.replace("\n", " | "));
        for (SocketAddress addr : clients.values()) sendTo(addr, msg);
    }

    // Normaliza strings para comparação (remove acentos, converte para minúsculas e trim)
    private String normalize(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    // Obtém o nome em português do país, se disponível
    private String getPortugueseName(Pais pais) {
        if (pais.getTranslations() != null && pais.getTranslations().containsKey("por")) {
            Translation ptTranslation = pais.getTranslations().get("por");
            if (ptTranslation != null && ptTranslation.getCommon() != null) {
                return ptTranslation.getCommon();
            }
        }
        return pais.getName().getCommon(); // Fallback para o nome comum
    }

    // Formata as informações completas do país para exibição
    private String formatCountryFull(Pais c) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("pt","BR"));
        nf.setMaximumFractionDigits(0);
        return "Nome: " + getPortugueseName(c) + "\n" +
               "Continente: " + (c.getRegion() == null ? "—" : c.getRegion()) + "\n" +
               "Capital: " + getCapital(c) + "\n" +
               "Área (km²): " + nf.format((long)c.getArea()) + "\n" +
               "População: " + nf.format(c.getPopulation()) + "\n" +
               "Línguas: " + getLanguages(c) + "\n";
    }
}