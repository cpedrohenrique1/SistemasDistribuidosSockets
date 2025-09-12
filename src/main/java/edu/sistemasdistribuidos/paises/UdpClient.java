package edu.sistemasdistribuidos.paises;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;   
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/*
 * Manual de como jogar:
 * 1. Compile e execute o UdpServer.java em um terminal.
 * 2. Em outro terminal, compile e execute o UdpClient.java.
 * 3. No cliente, digite seus palpites para o país escolhido pelo servidor.
 * 4. Para desistir, digite "desisto".
 * 5. O servidor informará se o palpite está correto ou não.
 * 6. Divirta-se!
 */
public class UdpClient {
    // Altere para o IP do servidor se necessário
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        new UdpClient().run();
    }

    // Lógica principal do cliente
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(0);
            InetAddress serverAddr = InetAddress.getByName(SERVER_HOST);

            // Listener para mensagens do servidor
            Thread listener = new Thread(() -> {
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket pack = new DatagramPacket(buf, buf.length);
                while (true) {
                    try {
                        socket.receive(pack);
                        String msg = new String(pack.getData(), 0, pack.getLength(), StandardCharsets.UTF_8);
                        if (msg.equals("SHUTDOWN") || msg.equals("shutdown")) {
                            System.out.println("[CLIENT] O jogo acabou. Desconectando.");
                            System.exit(0);
                        }
                        System.out.println(msg);
                    } catch (IOException e) {
                        if (socket.isClosed()) {
                            break;
                        }
                        System.err.println("[CLIENT] Erro de recebimento: " + e.getMessage());
                        break;
                    }
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Envia JOIN para o servidor
            send(socket, "JOIN", serverAddr, SERVER_PORT);
            System.out.println("[CLIENT] JOIN enviado. Digite seus palpites (ou 'desisto' para sair).");

            // Loop para ler palpites do usuário
            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
            while (true) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                // Envia o palpite ou a desistência
                if (line.equalsIgnoreCase("desisto")) {
                    send(socket, "DESISTO", serverAddr, SERVER_PORT);
                    System.out.println("[CLIENT] Você desistiu. Saindo.");
                    try {
                        // tempo para garantir que a mensagem seja reccebida antes de fechar
                        Thread.sleep(500); 
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                } else {
                    send(socket, "GUESS:" + line, serverAddr, SERVER_PORT);
                }
            }

        } catch (SocketException se) {
            System.err.println("[CLIENT] Socket erro: " + se.getMessage());
        } catch (UnknownHostException ue) {
            System.err.println("[CLIENT] Host desconhecido: " + ue.getMessage());
        }
    }

    // Método auxiliar para enviar mensagens ao servidor
    private void send(DatagramSocket socket, String message, InetAddress host, int port) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket dp = new DatagramPacket(data, data.length, host, port);
            socket.send(dp);
        } catch (IOException e) {
            System.err.println("[CLIENT] Erro de envio: " + e.getMessage());
        }
    }
}